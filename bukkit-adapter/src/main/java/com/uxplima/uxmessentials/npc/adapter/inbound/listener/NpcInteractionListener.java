package com.uxplima.uxmessentials.npc.adapter.inbound.listener;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcRenderer;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Runs an NPC's bound click command when a player interacts with it. A packet NPC has no real entity, so the
 * server never knows it was clicked through the normal interact event — instead Paper fires
 * {@link PlayerUseUnknownEntityEvent} carrying the fake entity id. The renderer resolves that id back to the NPC
 * name (the id is allocated once per NPC and shared by every viewer), the repository supplies the stored command,
 * and it runs on the clicking player's region thread (where {@code performCommand} / {@code dispatchCommand} are
 * safe).
 *
 * <p>The command may carry a routing prefix: {@code [console]} runs it as the server console, {@code [player]}
 * (or no prefix) as the clicking player; a {@code {player}} token is replaced with the clicker's name. Only a
 * right-click/interact with the main hand is acted on — the event fires once per hand and a held button repeats,
 * so a left-click (attack) and the off-hand are ignored and a short per-player-per-NPC cooldown swallows the
 * duplicate the client still sends. The interaction runs for everyone (an NPC is a server fixture); the reserved
 * {@code uxmessentials.npc.use} node is left for a future per-NPC permission gate rather than blanket-gating the
 * click here.
 */
@NullMarked
public final class NpcInteractionListener implements Listener {

    private static final String CONSOLE_PREFIX = "[console]";
    private static final String PLAYER_PREFIX = "[player]";
    private static final String PLAYER_TOKEN = "{player}";

    private final NpcRenderer renderer;
    private final NpcRepository repository;
    private final NpcCommandRunner runner;
    private final Scheduler scheduler;
    private final long cooldownMillis;
    private final LongSupplier clock;
    private final Map<String, Long> lastClick = new ConcurrentHashMap<>();

    public NpcInteractionListener(
            NpcRenderer renderer,
            NpcRepository repository,
            NpcCommandRunner runner,
            Scheduler scheduler,
            Duration clickCooldown) {
        this(renderer, repository, runner, scheduler, clickCooldown, System::currentTimeMillis);
    }

    NpcInteractionListener(
            NpcRenderer renderer,
            NpcRepository repository,
            NpcCommandRunner runner,
            Scheduler scheduler,
            Duration clickCooldown,
            LongSupplier clock) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.cooldownMillis =
                Objects.requireNonNull(clickCooldown, "clickCooldown").toMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUseUnknownEntity(PlayerUseUnknownEntityEvent event) {
        if (event.isAttack() || event.getHand() != EquipmentSlot.HAND) {
            return; // interact (right-click) with the main hand only; left-click and the off-hand duplicate are ignored
        }
        String npcName = renderer.npcNameAt(event.getEntityId());
        if (npcName == null) {
            return; // a genuinely unknown entity (not one of ours)
        }
        Player player = event.getPlayer();
        if (onCooldown(player, npcName)) {
            return;
        }
        Optional<Npc> npc = repository.find(NpcName.of(npcName));
        if (npc.isEmpty() || !npc.get().hasClickCommand()) {
            return;
        }
        run(player, Objects.requireNonNull(npc.get().clickCommand(), "clickCommand"));
    }

    private boolean onCooldown(Player player, String npcName) {
        if (cooldownMillis <= 0) {
            return false;
        }
        long now = clock.getAsLong();
        String key = player.getUniqueId() + ":" + npcName;
        Long previous = lastClick.put(key, now);
        return previous != null && (now - previous) < cooldownMillis;
    }

    private void run(Player player, String rawCommand) {
        String substituted = rawCommand.replace(PLAYER_TOKEN, player.getName());
        String lower = substituted.toLowerCase(Locale.ROOT);
        // The command runs on the clicking player's region thread, where performCommand/dispatchCommand are safe.
        if (lower.startsWith(CONSOLE_PREFIX)) {
            String command = substituted.substring(CONSOLE_PREFIX.length()).strip();
            scheduler.onEntity(BukkitRefs.toRef(player), () -> runner.runAsConsole(command));
            return;
        }
        String command = lower.startsWith(PLAYER_PREFIX)
                ? substituted.substring(PLAYER_PREFIX.length()).strip()
                : substituted.strip();
        scheduler.onEntity(BukkitRefs.toRef(player), () -> runner.runAsPlayer(player, command));
    }
}
