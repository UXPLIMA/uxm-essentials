package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.holograms.application.port.HologramTeleporter;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link HologramTeleporter} on Paper's {@code teleportAsync}, for {@code /hologram teleport}. An operator
 * inspecting a hologram is moved straight there — this is an admin convenience, not a gated player teleport, so
 * it deliberately bypasses the teleport context's cooldown/warmup and hops directly.
 *
 * <p>The hop is driven from the player's own entity scheduler (so it is valid on Folia), then resolves the live
 * player and {@code teleportAsync}es them; {@code teleportAsync} performs the region hop and async chunk load
 * internally. A player who logged off between the command and the hop is a no-op, and an unloaded destination
 * world is logged and skipped rather than throwing.
 */
@NullMarked
public final class HologramTeleportAdapter implements HologramTeleporter {

    private final Scheduler scheduler;
    private final Logger log;

    public HologramTeleportAdapter(Scheduler scheduler, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void teleport(PlayerRef who, Position destination) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(destination, "destination");
        scheduler.onEntity(who, () -> hop(who, destination));
    }

    private void hop(PlayerRef who, Position destination) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return; // logged off between the command and the hop — nothing to move
        }
        World world = Bukkit.getWorld(destination.world().uid());
        if (world == null) {
            log.warn(
                    "hologram teleport world {} is not loaded for {}",
                    destination.world().name(),
                    who.name());
            return;
        }
        Location to = BukkitRefs.toLocation(world, destination);
        var ignored = player.teleportAsync(to);
    }
}
