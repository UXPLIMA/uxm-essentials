package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ForcedGameMode;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import org.jspecify.annotations.NullMarked;

/** Forces the configured per-world gamemode on players entering a world (unless they hold the bypass node). */
@NullMarked
public final class ForceGamemodeListener implements Listener {

    public static final String BYPASS_NODE = "uxmessentials.world.gamemode.bypass";

    private final WorldRepository repository;
    private final Scheduler scheduler;

    public ForceGamemodeListener(WorldRepository repository, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        enforce(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        enforce(event.getPlayer());
    }

    private void enforce(Player player) {
        if (player.hasPermission(BYPASS_NODE)) {
            return;
        }
        ForcedGameMode forced = forcedFor(player);
        if (forced == ForcedGameMode.NONE) {
            return;
        }
        GameMode target = toBukkit(forced);
        PlayerRef who = BukkitRefs.toRef(player);
        scheduler.onEntity(who, () -> {
            Player live = Bukkit.getPlayer(who.uuid());
            if (live != null && live.getGameMode() != target) {
                live.setGameMode(target);
            }
        });
    }

    private ForcedGameMode forcedFor(Player player) {
        WorldName name;
        try {
            name = WorldName.of(player.getWorld().getName());
        } catch (IllegalArgumentException unusableName) {
            return ForcedGameMode.NONE;
        }
        return repository
                .find(name)
                .map(world -> world.settings().get(WorldProperties.FORCE_GAMEMODE))
                .orElse(ForcedGameMode.NONE);
    }

    private static GameMode toBukkit(ForcedGameMode mode) {
        return switch (mode) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
            case NONE -> throw new IllegalStateException("NONE filtered above");
        };
    }
}
