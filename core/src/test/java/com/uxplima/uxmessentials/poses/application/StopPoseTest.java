package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.poses.application.port.PoseReturn;
import com.uxplima.uxmessentials.poses.application.port.SeatHandle;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.event.PoseEnded;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link StopPose}: ending a pose removes the exact seat the session recorded, returns the player when the
 * server is configured to (and only when the exit allows it), clears the session, and publishes {@link PoseEnded};
 * a player who is not posing is a safe no-op.
 */
class StopPoseTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef CARRIER = new PlayerRef(UUID.randomUUID(), "Carrier");
    private static final Position RETURN = new Position(WORLD, 11.0, 64.0, 21.0, 0f, 0f);

    private final PoseSessions sessions = new PoseSessions();
    private final RecordingSeatPort seats = new RecordingSeatPort();
    private final RecordingReturn poseReturn = new RecordingReturn();
    private final RecordingEvents events = new RecordingEvents();

    @Test
    void dismountsRemovesReturnsClearsAndFires() {
        seed("h1");
        StopPose stopPose = new StopPose(sessions, seats, poseReturn, events, true);

        assertThat(stopPose.stop(WHO)).isPresent();

        assertThat(seats.removed).containsExactly("h1");
        assertThat(poseReturn.returns).containsExactly(RETURN);
        assertThat(sessions.isPosing(WHO)).isFalse();
        assertThat(events.published).singleElement().isInstanceOf(PoseEnded.class);
    }

    @Test
    void dismountsTheRiderRatherThanRemovingASeatForAPlayerSit() {
        // A player-sit rides a carrier, not a seat entity, so stopping it dismounts the rider — no seat is removed.
        sessions.start(new PoseSession(
                WHO, PoseType.PLAYER_SIT, RETURN, "player-sit", CARRIER, Instant.parse("2026-07-02T00:00:00Z")));
        StopPose stopPose = new StopPose(sessions, seats, poseReturn, events, true);

        assertThat(stopPose.stop(WHO)).isPresent();

        assertThat(seats.dismounted).containsExactly(WHO);
        assertThat(seats.removed).isEmpty();
        assertThat(sessions.isPosing(WHO)).isFalse();
        assertThat(events.published).singleElement().isInstanceOf(PoseEnded.class);
    }

    @Test
    void isASafeNoOpWhenThePlayerIsNotPosing() {
        StopPose stopPose = new StopPose(sessions, seats, poseReturn, events, true);

        assertThat(stopPose.stop(WHO)).isEmpty();
        assertThat(seats.removed).isEmpty();
        assertThat(poseReturn.returns).isEmpty();
        assertThat(events.published).isEmpty();
    }

    @Test
    void suppressesTheReturnWhenTheExitDisallowsIt() {
        seed("h1");
        StopPose stopPose = new StopPose(sessions, seats, poseReturn, events, true);

        stopPose.stop(WHO, false);

        // The seat is still removed (no ghost), but the player is not teleported — this is the quit/teleport exit.
        assertThat(seats.removed).containsExactly("h1");
        assertThat(poseReturn.returns).isEmpty();
    }

    @Test
    void skipsTheReturnWhenReturnToStartIsOff() {
        seed("h1");
        StopPose stopPose = new StopPose(sessions, seats, poseReturn, events, false);

        stopPose.stop(WHO);

        assertThat(seats.removed).containsExactly("h1");
        assertThat(poseReturn.returns).isEmpty();
    }

    private void seed(String handle) {
        sessions.start(new PoseSession(WHO, PoseType.SIT, RETURN, handle, null, Instant.parse("2026-07-02T00:00:00Z")));
    }

    /** Records the handles the port was asked to remove and the riders it was asked to dismount. */
    private static final class RecordingSeatPort implements SeatPort {
        private final List<String> removed = new ArrayList<>();
        private final List<PlayerRef> dismounted = new ArrayList<>();

        @Override
        public SeatHandle spawnSeat(Position seat, float yaw) {
            return SeatHandle.of("unused");
        }

        @Override
        public void mount(PlayerRef rider, SeatHandle seat) {}

        @Override
        public void mountOnPlayer(PlayerRef rider, PlayerRef target) {}

        @Override
        public void dismount(PlayerRef rider) {
            dismounted.add(rider);
        }

        @Override
        public void removeSeat(SeatHandle seat) {
            removed.add(seat.id());
        }

        @Override
        public boolean isOccupied(Position seat) {
            return false;
        }

        @Override
        public int sweepOrphans() {
            return 0;
        }
    }

    /** Records the positions the return port was asked to teleport to. */
    private static final class RecordingReturn implements PoseReturn {
        private final List<Position> returns = new ArrayList<>();

        @Override
        public void returnTo(PlayerRef who, Position where) {
            returns.add(where);
        }
    }

    /** Collects the events the use case publishes. */
    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
