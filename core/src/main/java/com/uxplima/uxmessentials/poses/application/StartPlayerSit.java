package com.uxplima.uxmessentials.poses.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.event.PoseStarted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Starts a player-sit — the stacking pose where {@code who} sits on {@code target}'s shoulders (right-clicking a
 * player). Unlike a block-sit there is no seat entity: the rider is mounted straight onto the carrier through the
 * {@link SeatPort#mountOnPlayer port}, and because {@code addPassenger} chains, A-on-B-on-C stacking just works.
 *
 * <p>Four gates turn an attempt away before anything is mounted: the {@code features.player-sit} switch (the
 * operator's master toggle, off by default), sitting on yourself, being already posed (the one-session-per-player
 * invariant), and a target whose personal {@link PlayerSitPreferences} refuses. On success it records a
 * {@link PoseType#PLAYER_SIT} session carrying the {@code target} and the rider's captured standing position (so
 * standing up returns them there), and publishes {@link PoseStarted}.
 */
public final class StartPlayerSit {

    /**
     * The session's seat-handle for a player-sit. There is no seat entity to remove, so the handle is never used to
     * reap one — {@code StopPose} dismounts the rider from the carrier instead. It is stored only to satisfy the
     * {@link PoseSession} contract's non-null handle.
     */
    private static final String NO_SEAT_HANDLE = "player-sit";

    private final PoseSessions sessions;
    private final SeatPort seats;
    private final PlayerSitPreferences preferences;
    private final PlayerLocator locator;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final boolean playerSitEnabled;

    public StartPlayerSit(
            PoseSessions sessions,
            SeatPort seats,
            PlayerSitPreferences preferences,
            PlayerLocator locator,
            DomainEventPublisher events,
            Clock clock,
            boolean playerSitEnabled) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.seats = Objects.requireNonNull(seats, "seats");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.locator = Objects.requireNonNull(locator, "locator");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.playerSitEnabled = playerSitEnabled;
    }

    /** Seat {@code who} on {@code target}. Returns the outcome the adapter renders feedback from. */
    public PlayerSitOutcome start(PlayerRef who, PlayerRef target) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(target, "target");
        if (!playerSitEnabled) {
            return PlayerSitOutcome.DISABLED;
        }
        if (who.equals(target)) {
            return PlayerSitOutcome.SELF;
        }
        if (sessions.isPosing(who)) {
            return PlayerSitOutcome.ALREADY_POSING;
        }
        if (!preferences.allowsSitting(target)) {
            return PlayerSitOutcome.TARGET_REFUSES;
        }
        mountAndRecord(who, target);
        return PlayerSitOutcome.STARTED;
    }

    private void mountAndRecord(PlayerRef who, PlayerRef target) {
        seats.mountOnPlayer(who, target);
        Position returnLocation = locator.locate(who)
                .orElseThrow(() -> new IllegalStateException("cannot player-sit an unlocatable rider: " + who.uuid()));
        PoseSession session =
                new PoseSession(who, PoseType.PLAYER_SIT, returnLocation, NO_SEAT_HANDLE, target, clock.instant());
        sessions.start(session);
        events.publish(new PoseStarted(session));
    }

    /** How a player-sit attempt resolved, so the adapter can send the matching feedback. */
    public enum PlayerSitOutcome {

        /** The pose began; the rider is mounted on the carrier. */
        STARTED,

        /** {@code features.player-sit} is off — sitting on players is not offered. */
        DISABLED,

        /** The target refuses being sat on (their {@code /poses toggle} opt-out). */
        TARGET_REFUSES,

        /** The rider already holds a pose; the one-session invariant turns the second attempt away. */
        ALREADY_POSING,

        /** The rider tried to sit on themselves. */
        SELF
    }
}
