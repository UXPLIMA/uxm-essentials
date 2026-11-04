package com.uxplima.uxmessentials.guard.adapter.movement;

import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import java.util.Map;
import com.uxplima.uxmessentials.guard.application.GuardMessageKey;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

/**
 * Feeds player movement into {@link PlayerTracks}. A move inside one block does nothing; a move to another block
 * is one region set lookup; only a change of region set runs rules and behaviours.
 */
public final class MovementListener implements Listener {

    private final GuardRuntime guard;
    private final PlayerTracks tracks;

    public MovementListener(GuardRuntime guard, PlayerTracks tracks) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.tracks = Objects.requireNonNull(tracks, "tracks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ() && from.getWorld() == to.getWorld()) {
            return;
        }
        Location result = tracks.moved(event.getPlayer(), from, to, false);
        if (result == null) {
            event.setCancelled(true);
        } else if (result != to) {
            event.setTo(result);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN
                && event.getFrom().getWorld() == to.getWorld()
                && event.getFrom().distanceSquared(to) < 4) {
            return;
        }
        Location result = tracks.moved(event.getPlayer(), event.getFrom(), to, true);
        if (result == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player) {
                Location from = event.getFrom();
                Location to = event.getTo();
                if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()
                        && from.getBlockY() == to.getBlockY()) {
                    continue;
                }
                Location result = tracks.moved(player, from, to, false);
                if (result != to) {
                    event.getVehicle().eject();
                    java.util.concurrent.CompletableFuture<Boolean> unused =
                            player.teleportAsync(result == null ? from : result);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location at = player.getLocation();
        if (at != null) {
            tracks.placed(player, at);
        }
        if (guard.service().loadFailed() && player.hasPermission(GuardRuntime.NOTIFY_PERMISSION)) {
            guard.send(player, GuardMessageKey.LOCKED_WORLD_STAFF, Map.of(), "chat");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        tracks.placed(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        tracks.refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tracks.left(event.getPlayer());
        guard.forget(event.getPlayer().getUniqueId());
    }
}
