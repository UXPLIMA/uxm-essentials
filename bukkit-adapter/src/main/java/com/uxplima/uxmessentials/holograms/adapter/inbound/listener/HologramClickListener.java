package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramClickKey;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramPageCycler;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Acts on a clickable hologram when a player right-clicks its Interaction hitbox. The hitbox carries the
 * hologram's name in its persistent data (stamped by the renderer under {@link HologramClickKey#PDC_KEY}); this
 * reads it back, looks the hologram up in the in-memory-authoritative repository (no DB read on the click), and
 * acts on the clicking player's region thread (hopped through the {@link Scheduler} port, where {@code
 * performCommand} / page packets / the action chain are safe).
 *
 * <p>The legacy single click command runs first (a command takes precedence; a multi-page hologram without a
 * command cycles the viewer's page instead). Then the ordered action chain — every
 * {@link com.uxplima.uxmessentials.shared.domain.action.ClickAction} whose trigger matches the click — runs through
 * the shared {@link ClickActionRunner}, so a hologram may carry a legacy click command <em>and</em> an action
 * chain together (additive, exactly as an NPC keeps its click command alongside its actions). A short per-player
 * cooldown debounces the rapid duplicate interactions a single click can produce.
 */
@NullMarked
public final class HologramClickListener implements Listener {

    private static final long COOLDOWN_MS = 250L;

    private final NamespacedKey clickKey;
    private final HologramRepository repository;
    private final HologramPageCycler pageCycler;
    private final ClickActionRunner actionRunner;
    private final Scheduler scheduler;
    private final ConcurrentHashMap<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public HologramClickListener(
            Plugin plugin,
            HologramRepository repository,
            HologramPageCycler pageCycler,
            ClickActionRunner actionRunner,
            Scheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        this.clickKey = new NamespacedKey(plugin, HologramClickKey.PDC_KEY);
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pageCycler = Objects.requireNonNull(pageCycler, "pageCycler");
        this.actionRunner = Objects.requireNonNull(actionRunner, "actionRunner");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Interaction box)) {
            return; // main hand only; the off-hand fires its own duplicate event we ignore
        }
        String name = box.getPersistentDataContainer().get(clickKey, PersistentDataType.STRING);
        if (name == null) {
            return;
        }
        Player player = event.getPlayer();
        if (onCooldown(player.getUniqueId())) {
            return;
        }
        Hologram hologram = repository.find(HologramName.of(name)).orElse(null);
        if (hologram == null) {
            return;
        }
        // Hop onto the clicking player's entity region: the runner requires the viewer's region thread, and a
        // Folia interact event may not already own it.
        scheduler.onEntity(BukkitRefs.toRef(player), () -> act(player, hologram));
    }

    private void act(Player player, Hologram hologram) {
        String command = hologram.clickCommand();
        if (command != null && !command.isBlank()) {
            // A command takes precedence; a multi-page hologram without a command cycles the viewer's page.
            player.performCommand(command);
        } else if (hologram.isMultiPage()) {
            pageCycler.cyclePage(player, hologram.name());
        }
        // The action chain always runs after the legacy behaviour, so a hologram may have both (additive); it
        // filters itself by per-action trigger. This right-click path passes attack=false.
        if (hologram.hasActions()) {
            actionRunner.run(player, hologram.actions(), false);
        }
    }

    /** True when this player clicked within the debounce window; records the click time otherwise. */
    private boolean onCooldown(UUID player) {
        long now = System.currentTimeMillis();
        Long previous = lastClick.put(player, now);
        return previous != null && now - previous < COOLDOWN_MS;
    }
}
