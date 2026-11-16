package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
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
import com.uxplima.uxmessentials.teleport.application.port.PendingRelocations;
import com.uxplima.uxmessentials.teleport.application.port.RequestVisibility;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.PendingRelocation;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * {@code /tpoffline} sends staff to where an offline player left this server; {@code /tpofflinehere} leaves a move
 * that this server applies once, after the player next joins it. A player online here is never read or stored.
 */
class OfflineTeleportTest {

    private static final String SURVIVAL = "survival";
    private static final Position HERE = new Position(WORLD, 5.5, 70, -3.5, 45f, 10f);

    private final SavedData saved = new SavedData();
    private final Relocations relocations = new Relocations();
    private final Players players = new Players();
    private final Hidden hidden = new Hidden();
    private final Moves moves = new Moves();
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final RecordingNotifier notifier = new RecordingNotifier();

    private OfflineTeleport on(String server) {
        return new OfflineTeleport(
                saved,
                relocations,
                players,
                new Worlds(),
                hidden,
                moves,
                scheduler,
                notifier.notifier(),
                new MutableClock(NOW),
                server,
                new QuietLog());
    }

    private final OfflineTeleport offline = on(SURVIVAL);

    @Test
    void staffLandWhereTheOfflinePlayerLeftThisServer() {
        saved.spots.put(SAM.uuid(), new LogoutPositions.SavedSpot(WORLD.uid(), 12.5, 64, 80.5, 90f, 5f));

        offline.goTo(ALEX, "Sam");
        scheduler.runAll();

        assertThat(moves.moved).containsExactly(new Move(ALEX, at(12.5, 64, 80.5, 90f, 5f)));
        assertThat(notifier.keys()).containsExactly("teleport.tp.done");
    }

    @Test
    void aPlayerThisServerKeepsNoDataForMovesNobody() {
        offline.goTo(ALEX, "Sam");
        scheduler.runAll();

        assertThat(moves.moved).isEmpty();
        assertThat(notifier.sent())
                .containsExactly(
                        new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.unknown", Map.of("player", "Sam")));
    }

    @Test
    void savedDataThatCannotBeReadIsReportedApartFromMissingData() {
        saved.unreadable = true;

        offline.goTo(ALEX, "Sam");
        scheduler.runAll();

        assertThat(moves.moved).isEmpty();
        assertThat(notifier.sent())
                .containsExactly(
                        new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.failed", Map.of("player", "Sam")));
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
    void aBroughtPlayerIsMovedOnceAfterTheyNextJoinThisServer() {
        offline.bring(ALEX, "Sam", HERE);
        scheduler.runAll();
        assertThat(moves.moved).isEmpty();
        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.moved", Map.of("player", "Sam")));

        offline.arrive(SAM);
        offline.arrive(SAM);
        scheduler.runAll();

        assertThat(moves.moved).containsExactly(new Move(SAM, HERE));
        assertThat(relocations.stored).isEmpty();
    }

    @Test
    void aLaterBringReplacesTheEarlierOne() {
        offline.bring(ALEX, "Sam", HERE);
        scheduler.runAll();
        Position later = at(-20.5, 80, 3.5, 0f, 0f);
        offline.bring(ALEX, "Sam", later);
        scheduler.runAll();

        offline.arrive(SAM);
        scheduler.runAll();

        assertThat(moves.moved).containsExactly(new Move(SAM, later));
    }

    @Test
    void aMoveLeftOnOneServerWaitsForTheirJoinThere() {
        on("lobby").bring(ALEX, "Sam", HERE);
        scheduler.runAll();

        offline.arrive(SAM);
        scheduler.runAll();
        assertThat(moves.moved).isEmpty();

        on("lobby").arrive(SAM);
        scheduler.runAll();
        assertThat(moves.moved).containsExactly(new Move(SAM, HERE));
    }

    @Test
    void aMoveIntoAWorldThatIsNotLoadedWaitsForALaterJoin() {
        offline.bring(ALEX, "Sam", new Position(new WorldRef(UUID.randomUUID(), "nether"), 0, 64, 0, 0f, 0f));
        scheduler.runAll();

        offline.arrive(SAM);
        scheduler.runAll();

        assertThat(moves.moved).isEmpty();
        assertThat(relocations.stored).hasSize(1);
    }

    @Test
    void aMoveThatMissesIsKeptForTheNextJoin() {
        offline.bring(ALEX, "Sam", HERE);
        scheduler.runAll();
        moves.misses = true;

        offline.arrive(SAM);
        scheduler.runAll();
        assertThat(relocations.stored).hasSize(1);

        moves.misses = false;
        offline.arrive(SAM);
        scheduler.runAll();
        assertThat(moves.moved).containsExactly(new Move(SAM, HERE));
        assertThat(relocations.stored).isEmpty();
    }

    @Test
    void aPlayerOnlineHereIsNeitherReadNorStored() {
        saved.spots.put(SAM.uuid(), new LogoutPositions.SavedSpot(WORLD.uid(), 0, 64, 0, 0f, 0f));
        players.online.add(SAM.uuid());

        offline.goTo(ALEX, "Sam");
        offline.bring(ALEX, "Sam", HERE);
        scheduler.runAll();

        assertThat(saved.reads).isZero();
        assertThat(relocations.stored).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.tpoffline.online", "teleport.tpoffline.online");
    }

    @Test
    void aVanishedPlayerOnlineHereReadsAsUnknownToStaffWhoCannotSeeThem() {
        players.online.add(SAM.uuid());
        hidden.fromAlex.add(SAM);

        offline.goTo(ALEX, "sam");
        offline.bring(ALEX, "sam", HERE);
        scheduler.runAll();

        assertThat(relocations.stored).isEmpty();
        assertThat(notifier.sent())
                .containsExactly(
                        new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.unknown", Map.of("player", "sam")),
                        new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.unknown", Map.of("player", "sam")));
    }

    @Test
    void aNameNobodyHasPlayedUnderIsTypedBackToTheStaffMember() {
        offline.goTo(ALEX, "Nobody");
        scheduler.runAll();

        assertThat(saved.reads).isZero();
        assertThat(notifier.sent())
                .containsExactly(
                        new RecordingNotifier.Sent(ALEX, "teleport.tpoffline.unknown", Map.of("player", "Nobody")));
    }

    private static Position at(double x, double y, double z, float yaw, float pitch) {
        return new Position(WORLD, x, y, z, yaw, pitch);
    }

    private record Move(PlayerRef who, Position to) {}

    /** The positions this server saved, or a read that fails as a file cut short does. */
    private static final class SavedData implements LogoutPositions {

        final Map<UUID, SavedSpot> spots = new HashMap<>();
        boolean unreadable;
        int reads;

        @Override
        public Optional<SavedSpot> read(UUID player) {
            reads++;
            if (unreadable) {
                throw new Unreadable("cut short");
            }
            return Optional.ofNullable(spots.get(player));
        }
    }

    /** The shared relocation table: one row per player and server, deleted only while it is unchanged. */
    private static final class Relocations implements PendingRelocations {

        final Map<String, PendingRelocation> stored = new HashMap<>();

        private static String key(UUID player, String server) {
            return player + "@" + server;
        }

        @Override
        public void save(PendingRelocation relocation) {
            stored.put(key(relocation.player(), relocation.server()), relocation);
        }

        @Override
        public Optional<PendingRelocation> find(UUID player, String server) {
            return Optional.ofNullable(stored.get(key(player, server)));
        }

        @Override
        public boolean claim(PendingRelocation relocation) {
            return stored.remove(key(relocation.player(), relocation.server()), relocation);
        }

        @Override
        public void restore(PendingRelocation relocation) {
            stored.putIfAbsent(key(relocation.player(), relocation.server()), relocation);
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

    /** The players vanished from Alex. */
    private static final class Hidden implements RequestVisibility {

        final Set<PlayerRef> fromAlex = new HashSet<>();

        @Override
        public int seeLevel(PlayerRef viewer) {
            return 0;
        }

        @Override
        public boolean hides(PlayerRef target, PlayerRef viewer, int viewerSeeLevel) {
            return viewer.equals(ALEX) && fromAlex.contains(target);
        }

        @Override
        public boolean hiddenElsewhere(PlayerRef target, int viewerSeeLevel) {
            return false;
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

    /** Keeps where each player was sent, or misses every hop as a player who left again would. */
    private static final class Moves implements TeleportExecutor {

        final List<Move> moved = new ArrayList<>();
        boolean misses;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            moved.add(new Move(who, destination.position()));
        }

        @Override
        public void teleport(
                PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded, Runnable onFailed) {
            if (misses) {
                onFailed.run();
                return;
            }
            teleport(who, destination, kind);
            onLanded.run();
        }
    }
}
