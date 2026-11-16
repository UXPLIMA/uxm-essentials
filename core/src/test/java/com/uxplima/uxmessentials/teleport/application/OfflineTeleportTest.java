package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.LogoutPositions;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * {@code /tpoffline} sends staff to where an offline player left this server, and {@code /tpofflinehere} makes
 * that player log in where the staff member stands. A player online here is never read or written.
 */
class OfflineTeleportTest {

    private static final Position HERE = new Position(WORLD, 5.5, 70, -3.5, 45f, 10f);

    private final SavedData saved = new SavedData();
    private final Players players = new Players();
    private final Moves moves = new Moves();
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final OfflineTeleport offline =
            new OfflineTeleport(saved, players, new Worlds(), moves, scheduler, notifier.notifier());

    @Test
    void staffLandWhereTheOfflinePlayerLeftThisServer() {
        saved.spots.put(SAM.uuid(), new LogoutPositions.SavedSpot(WORLD.uid(), 12.5, 64, 80.5, 90f, 5f));

        offline.goTo(ALEX, "Sam");
        scheduler.runAll();

        assertThat(moves.moved).containsExactly(new Move(ALEX, new Position(WORLD, 12.5, 64, 80.5, 90f, 5f)));
        assertThat(notifier.keys()).containsExactly("teleport.tp.done");
    }

    @Test
    void aPlayerThisServerKeepsNoDataForMovesNobody() {
        offline.goTo(ALEX, "Sam");
        offline.bring(ALEX, "Sam", HERE);
        scheduler.runAll();

        assertThat(moves.moved).isEmpty();
        assertThat(saved.spots).isEmpty();
        assertThat(notifier.sent())
                .containsExactly(
                        new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.unknown", Map.of("player", "Sam")),
                        new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.unknown", Map.of("player", "Sam")));
    }

    @Test
    void aPlayerSavedInAWorldThatIsGoneMovesNobody() {
        saved.spots.put(SAM.uuid(), new LogoutPositions.SavedSpot(UUID.randomUUID(), 0, 64, 0, 0f, 0f));

        offline.goTo(ALEX, "Sam");
        scheduler.runAll();

        assertThat(moves.moved).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.tpoffline.world-missing");
    }

    @Test
    void bringingSavesTheStaffPositionAsWhereTheyLogInNext() {
        saved.spots.put(SAM.uuid(), new LogoutPositions.SavedSpot(WORLD.uid(), 0, 64, 0, 0f, 0f));

        offline.bring(ALEX, "Sam", HERE);
        scheduler.runAll();

        assertThat(saved.spots.get(SAM.uuid()))
                .isEqualTo(new LogoutPositions.SavedSpot(WORLD.uid(), 5.5, 70, -3.5, 45f, 10f));
        assertThat(moves.moved).isEmpty();
        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.moved", Map.of("player", "Sam")));
    }

    @Test
    void aPlayerOnlineHereIsNeitherReadNorWritten() {
        saved.spots.put(SAM.uuid(), new LogoutPositions.SavedSpot(WORLD.uid(), 0, 64, 0, 0f, 0f));
        players.online.add(SAM.uuid());

        offline.goTo(ALEX, "Sam");
        offline.bring(ALEX, "Sam", HERE);
        scheduler.runAll();

        assertThat(saved.touched).isZero();
        assertThat(moves.moved).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.tpoffline.online", "teleport.tpoffline.online");
    }

    @Test
    void aNameNobodyHasPlayedUnderIsTypedBackToTheStaffMember() {
        offline.goTo(ALEX, "Nobody");
        scheduler.runAll();

        assertThat(saved.touched).isZero();
        assertThat(notifier.sent())
                .containsExactly(
                        new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.unknown", Map.of("player", "Nobody")));
    }

    private record Move(PlayerRef who, Position to) {}

    /** The saved positions of one server, counting every read and write. */
    private static final class SavedData implements LogoutPositions {

        final Map<UUID, SavedSpot> spots = new HashMap<>();
        int touched;

        @Override
        public Optional<SavedSpot> read(UUID player) {
            touched++;
            return Optional.ofNullable(spots.get(player));
        }

        @Override
        public boolean write(UUID player, Position at) {
            touched++;
            if (!spots.containsKey(player)) {
                return false;
            }
            spots.put(player, new SavedSpot(at.world().uid(), at.x(), at.y(), at.z(), at.yaw(), at.pitch()));
            return true;
        }
    }

    /** Alex and Sam have played here; only the players in {@link #online} are on this server now. */
    private static final class Players implements PlayerLookup {

        final Set<UUID> online = new HashSet<>();

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return findByName(name).filter(ref -> online.contains(ref.uuid()));
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return List.of(ALEX, SAM).stream()
                    .filter(ref -> ref.name().equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return List.of(ALEX, SAM).stream()
                    .filter(ref -> ref.uuid().equals(uuid))
                    .findFirst();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return online.contains(uuid);
        }
    }

    /** Only the fixture world is loaded. */
    private static final class Worlds implements WorldLookup {

        @Override
        public Optional<WorldRef> findByName(String name) {
            return WORLD.name().equals(name) ? Optional.of(WORLD) : Optional.empty();
        }

        @Override
        public Optional<WorldRef> findByUid(UUID uid) {
            return WORLD.uid().equals(uid) ? Optional.of(WORLD) : Optional.empty();
        }
    }

    /** Keeps where each player was sent. */
    private static final class Moves implements TeleportExecutor {

        final List<Move> moved = new ArrayList<>();

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            moved.add(new Move(who, destination.position()));
        }
    }
}
