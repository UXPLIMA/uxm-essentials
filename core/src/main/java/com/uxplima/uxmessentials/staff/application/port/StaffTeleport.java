package com.uxplima.uxmessentials.staff.application.port;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Soft-coupled seam to the teleport context: send a staff member to a target: the COMPASS gadget. Staff mode
 * does not own teleportation; it orchestrates the existing teleport module's admin teleport through this port
 * rather than moving the player itself.
 *
 * <p>The coupling is soft: when the teleport module is disabled the wiring binds
 * {@link #NONE}, so the COMPASS gadget degrades to "teleport could not be started" rather than failing
 * mirroring {@link StaffVanish#NONE} and {@link StaffInspector#NONE}.
 */
@NullMarked
public interface StaffTeleport {

    /** A no-op teleport: the binding when teleport is disabled; the teleport is never started. */
    StaffTeleport NONE = (staff, target) -> CompletableFuture.completedFuture(false);

    /**
     * Teleports {@code staff} to {@code target}'s location as an admin teleport (no cooldown, warmup, or
     * request prompt). The target's location is read on the target's own thread, which on Folia is not the staff
     * member's, so the answer comes later: {@code false} if the teleport could not be started, for instance when the
     * teleport module is off or either player is no longer reachable.
     */
    CompletionStage<Boolean> teleportTo(PlayerRef staff, PlayerRef target);

    /**
     * Sends {@code staff} to the player named {@code target} on another backend of the network, through the teleport
     * module's cross-server staff teleport, which tells the staff member how it went. {@code false} when that cannot
     * be started: the teleport module is off, or its cross-server teleports are.
     */
    default boolean teleportElsewhere(PlayerRef staff, String target) {
        return false;
    }
}
