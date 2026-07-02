package com.uxplima.uxmessentials.poses.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Ends a pose on every exit a seated player can take: they quit, teleport away, take damage, dismount the seat, or
 * start sneaking. {@link StopPose} removes the seat entity on each so no ghost is left behind. The quit and
 * teleport exits pass {@code allowReturn = false} — the player is leaving or already moving, so the
 * {@code return-to-start} teleport would be wrong there; the others return the player when the server is
 * configured to. Every branch is guarded by the session registry so a non-posing player is untouched.
 *
 * <p>A quit also ends the pose of anyone stacked <em>on</em> the leaver: when a player-sit carrier logs out, Bukkit
 * drops the rider as a passenger, but the rider's {@link PoseSession} would linger unless it is ended too. The quit
 * handler stops each of the carrier's direct riders, so no rider is left with a stuck session once its carrier is
 * gone (a rider stacked on that rider is in turn stopped when its own carrier's session ends).
 */
@NullMarked
public final class PoseCancelListener implements Listener {

    private final StopPose stopPose;
    private final PoseSessions sessions;

    public PoseCancelListener(StopPose stopPose, PoseSessions sessions) {
        this.stopPose = Objects.requireNonNull(stopPose, "stopPose");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        // Always attempt cleanup on quit so a seat can never outlive the player; no return teleport for a leaver.
        stopPose.stop(who, false);
        // End the pose of anyone stacked on the leaver too, so no rider is left with a session once its carrier is
        // gone. Snapshot first, then stop each — stopping mutates the registry ridersOf reads.
        for (PlayerRef rider : sessions.ridersOf(who)) {
            stopPose.stop(rider, false);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        endIfPosing(event.getPlayer(), false);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            endIfPosing(player, true);
        }
    }

    @EventHandler
    public void onDismount(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            endIfPosing(player, true);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            endIfPosing(event.getPlayer(), true);
        }
    }

    private void endIfPosing(Player player, boolean allowReturn) {
        PlayerRef who = BukkitRefs.toRef(player);
        if (sessions.isPosing(who)) {
            stopPose.stop(who, allowReturn);
        }
    }
}
