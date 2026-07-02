package com.uxplima.uxmessentials.poses.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.poses.application.port.PoseReturn;
import com.uxplima.uxmessentials.poses.application.port.SeatHandle;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.event.PoseEnded;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Ends a player's pose — by their own command, or because they moved, took damage, dismounted, teleported, or
 * quit. It pulls the active {@link PoseSession} from the registry (a player who is not posing is a safe no-op),
 * removes the seat entity the session recorded so no ghost is left behind, optionally returns the player to where
 * the pose began, clears the session, and publishes {@link PoseEnded}.
 *
 * <p>The {@code return-to-start} teleport is suppressed on the exits where it would be wrong: a quit (the player
 * is gone) and a teleport (returning them would fight the teleport that ended the pose) call
 * {@link #stop(PlayerRef, boolean)} with {@code allowReturn = false}. The command, sneak, damage, and dismount
 * exits use {@link #stop(PlayerRef)}, which returns the player when the server is configured to.
 */
public final class StopPose {

    private final PoseSessions sessions;
    private final SeatPort seats;
    private final PoseReturn poseReturn;
    private final DomainEventPublisher events;
    private final boolean returnToStart;

    public StopPose(
            PoseSessions sessions,
            SeatPort seats,
            PoseReturn poseReturn,
            DomainEventPublisher events,
            boolean returnToStart) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.seats = Objects.requireNonNull(seats, "seats");
        this.poseReturn = Objects.requireNonNull(poseReturn, "poseReturn");
        this.events = Objects.requireNonNull(events, "events");
        this.returnToStart = returnToStart;
    }

    /** End {@code who}'s pose, returning them to where it began when the server is configured to. */
    public Optional<PoseSession> stop(PlayerRef who) {
        return stop(who, true);
    }

    /**
     * End {@code who}'s pose. When {@code allowReturn} is false the {@code return-to-start} teleport is skipped
     * regardless of config, for the quit and teleport exits where returning the player would be wrong. Returns the
     * session that ended, or empty when the player was not posing.
     */
    public Optional<PoseSession> stop(PlayerRef who, boolean allowReturn) {
        Objects.requireNonNull(who, "who");
        Optional<PoseSession> ended = sessions.stop(who);
        if (ended.isEmpty()) {
            return Optional.empty();
        }
        PoseSession session = ended.get();
        seats.removeSeat(SeatHandle.of(session.seatHandle()));
        if (allowReturn && returnToStart) {
            poseReturn.returnTo(who, session.returnLocation());
        }
        events.publish(new PoseEnded(session));
        return ended;
    }
}
