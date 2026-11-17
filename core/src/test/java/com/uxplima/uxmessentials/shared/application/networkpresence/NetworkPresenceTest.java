package com.uxplima.uxmessentials.shared.application.networkpresence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresenceBroadcast;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.PresenceTraits;
import com.uxplima.uxmessentials.testing.MutableClock;
import org.junit.jupiter.api.Test;

/** Every backend knows which backend each online player is on, and recovers from lost frames and dead backends. */
class NetworkPresenceTest {

    private static final Duration HEARTBEAT = Duration.ofSeconds(30);
    private static final Duration LIVENESS = HEARTBEAT.multipliedBy(3);
    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-15T12:00:00Z"));
    private NameCase nameCase = NameCase.INSENSITIVE;
    /** The lower-cased names more than one account holds, as the name index would answer. */
    private final java.util.Set<String> sharedNames = new java.util.HashSet<>(java.util.Set.of("steve"));

    private final Backend lobby = new Backend("lobby-1");
    private final Backend survival = new Backend("survival-1");

    @Test
    void withoutTheNetworkTheLocalPlayersAreKnownByNameInAnyCase() {
        lobby.track.joined(STEVE);
        lobby.track.joined(ALEX);
        lobby.track.left(ALEX);

        assertThat(lobby.roster.findByName("sTeVe")).contains(new OnlinePlayer(STEVE, "lobby-1"));
        assertThat(lobby.roster.findByName("Alex")).isEmpty();
        assertThat(lobby.roster.onServer("lobby-1")).containsExactly(new OnlinePlayer(STEVE, "lobby-1"));
    }

    @Test
    void offlineModeCaseVariantsOnlineTogetherAreFoundOnlyByTheirOwnSpelling() {
        nameCase = NameCase.SENSITIVE;
        PlayerRef lowerSteve = new PlayerRef(UUID.randomUUID(), "steve");
        lobby.track.joined(STEVE);
        lobby.track.joined(lowerSteve);

        assertThat(lobby.roster.findByName("Steve")).contains(new OnlinePlayer(STEVE, "lobby-1"));
        assertThat(lobby.roster.findByName("steve")).contains(new OnlinePlayer(lowerSteve, "lobby-1"));
        assertThat(lobby.roster.findByName("STEVE")).isEmpty();
        assertThat(lobby.roster.matchByName("STEVE")).isInstanceOf(NameMatch.Ambiguous.class);
    }

    @Test
    void offlineModeALonePlayerOnlineUnderANameNobodyElseHoldsIsFoundInAnyCase() {
        nameCase = NameCase.SENSITIVE;
        sharedNames.clear();
        PlayerRef lowerSteve = new PlayerRef(UUID.randomUUID(), "steve");
        lobby.track.joined(lowerSteve);
        lobby.out.deliverAll(survival.track);

        assertThat(survival.roster.findByName("Steve")).contains(new OnlinePlayer(lowerSteve, "lobby-1"));
    }

    @Test
    void offlineModeAVariantOnlineElsewhereIsNotTheAccountSpelledAsTyped() {
        nameCase = NameCase.SENSITIVE;
        PlayerRef lowerSteve = new PlayerRef(UUID.randomUUID(), "steve");
        lobby.track.joined(lowerSteve);
        lobby.out.deliverAll(survival.track);

        assertThat(survival.roster.findByName("steve")).contains(new OnlinePlayer(lowerSteve, "lobby-1"));
        // Steve, offline, is another account; the steve online on the lobby is not who /msg Steve means.
        assertThat(survival.roster.findByName("Steve")).isEmpty();
        assertThat(survival.roster.matchByName("Steve")).isEqualTo(NameMatch.unknown());
    }

    @Test
    void caseVariantsHereAreNotSettledByAPlayerOnAnotherBackend() {
        PlayerRef lowerSteve = new PlayerRef(UUID.randomUUID(), "steve");
        lobby.track.joined(new PlayerRef(UUID.randomUUID(), "STEVE"));
        lobby.out.deliverAll(survival.track);
        survival.track.joined(STEVE);
        survival.track.joined(lowerSteve);

        assertThat(survival.roster.findByName("sTeVe")).isEmpty();
    }

    @Test
    void aJoinOnOneBackendIsSeenOnAnotherAndTeachesItsNameIndex() {
        lobby.track.joined(STEVE);
        lobby.out.deliverAll(survival.track);

        assertThat(survival.roster.findByName("steve")).contains(new OnlinePlayer(STEVE, "lobby-1"));
        assertThat(survival.roster.onServer("lobby-1")).containsExactly(new OnlinePlayer(STEVE, "lobby-1"));
        assertThat(survival.names.byName("STEVE")).contains(STEVE);
    }

    @Test
    void aPlayerSwitchingBackendsIsFoundOnTheNewOneWhenTheJoinArrivesBeforeTheQuit() {
        Backend hub = new Backend("hub-1");
        lobby.track.joined(STEVE);
        lobby.out.deliverAll(hub.track);
        clock.advance(Duration.ofSeconds(1));

        survival.track.joined(STEVE);
        lobby.track.left(STEVE);
        survival.out.deliverAll(hub.track);

        assertThat(hub.roster.find(STEVE.uuid())).contains(new OnlinePlayer(STEVE, "survival-1"));
        assertThat(hub.roster.onServer("lobby-1")).isEmpty();
        lobby.out.deliverAll(hub.track);
        assertThat(hub.roster.online()).containsExactly(new OnlinePlayer(STEVE, "survival-1"));
    }

    @Test
    void aPlayerSwitchingBackendsIsFoundOnTheNewOneWhenTheQuitArrivesBeforeTheJoin() {
        Backend hub = new Backend("hub-1");
        lobby.track.joined(STEVE);
        lobby.out.deliverAll(hub.track);
        clock.advance(Duration.ofSeconds(1));

        lobby.track.left(STEVE);
        survival.track.joined(STEVE);
        lobby.out.deliverAll(hub.track);
        assertThat(hub.roster.find(STEVE.uuid())).isEmpty();
        survival.out.deliverAll(hub.track);

        assertThat(hub.roster.online()).containsExactly(new OnlinePlayer(STEVE, "survival-1"));
    }

    @Test
    void aPlayerArrivingFromABackendTheyAreStillOnCameFromIt() {
        lobby.track.joined(STEVE);
        lobby.out.deliverAll(survival.track);

        assertThat(survival.roster.cameFrom(STEVE.uuid(), Duration.ofSeconds(3)))
                .contains("lobby-1");
    }

    @Test
    void aPlayerWhoLeftABackendWithinTheWindowCameFromItAndNotAfterIt() {
        lobby.track.joined(STEVE);
        lobby.track.left(STEVE);
        lobby.out.deliverAll(survival.track);

        clock.advance(Duration.ofSeconds(2));
        assertThat(survival.roster.cameFrom(STEVE.uuid(), Duration.ofSeconds(3)))
                .contains("lobby-1");
        clock.advance(Duration.ofSeconds(2));
        assertThat(survival.roster.cameFrom(STEVE.uuid(), Duration.ofSeconds(3)))
                .isEmpty();
    }

    @Test
    void aPlayerNoOtherBackendHadCameFromNowhereAndThisBackendDoesNotCount() {
        survival.track.joined(ALEX);

        assertThat(survival.roster.cameFrom(STEVE.uuid(), Duration.ofSeconds(3)))
                .isEmpty();
        assertThat(survival.roster.cameFrom(ALEX.uuid(), Duration.ofSeconds(3))).isEmpty();
    }

    @Test
    void aPlayerOnTwoOtherBackendsCameFromTheOneSeenLast() {
        Backend hub = new Backend("hub-1");
        lobby.track.joined(STEVE);
        lobby.out.deliverAll(survival.track);
        clock.advance(Duration.ofSeconds(1));
        hub.track.joined(STEVE);
        lobby.track.left(STEVE);
        hub.out.deliverAll(survival.track);

        assertThat(survival.roster.cameFrom(STEVE.uuid(), Duration.ofSeconds(3)))
                .contains("hub-1");
    }

    @Test
    void aSwitchSeenInOneMillisecondAnswersTheSameBackendEveryTime() {
        Backend hub = new Backend("hub-1");
        survival.track.joined(STEVE);
        lobby.track.joined(STEVE);

        survival.out.deliverAll(hub.track);
        lobby.out.deliverAll(hub.track);

        assertThat(hub.roster.find(STEVE.uuid())).contains(new OnlinePlayer(STEVE, "lobby-1"));
        assertThat(hub.roster.online()).containsExactly(new OnlinePlayer(STEVE, "lobby-1"));
    }

    @Test
    void twoJoinsDeliveredInReverseBothApply() {
        lobby.track.joined(STEVE);
        lobby.track.joined(ALEX);

        lobby.out.deliverReversed(survival.track);

        assertThat(survival.roster.onServer("lobby-1"))
                .containsExactly(new OnlinePlayer(ALEX, "lobby-1"), new OnlinePlayer(STEVE, "lobby-1"));
    }

    @Test
    void aJoinArrivingAfterItsQuitDoesNotBringThePlayerBack() {
        lobby.track.joined(STEVE);
        lobby.track.left(STEVE);

        lobby.out.deliverReversed(survival.track);

        assertThat(survival.roster.find(STEVE.uuid())).isEmpty();
    }

    @Test
    void aFrameFromBeforeARestartIsIgnored() {
        lobby.track.joined(STEVE);
        clock.advance(Duration.ofSeconds(5));
        Backend restarted = new Backend("lobby-1");
        restarted.track.joined(ALEX);
        restarted.out.deliverAll(survival.track);

        lobby.out.deliverAll(survival.track);

        assertThat(survival.roster.onServer("lobby-1")).containsExactly(new OnlinePlayer(ALEX, "lobby-1"));
    }

    @Test
    void aRosterCutIntoPartsAppliesOnlyOnceEveryPartArrived() {
        RosterStamp stamp = new RosterStamp(1L, 1L);

        survival.track.remoteRoster("lobby-1", stamp, false, 1, 2, List.of(plain(ALEX)));
        assertThat(survival.roster.online()).isEmpty();
        survival.track.remoteRoster("lobby-1", stamp, false, 0, 2, List.of(plain(STEVE)));

        assertThat(survival.roster.onServer("lobby-1"))
                .containsExactly(new OnlinePlayer(ALEX, "lobby-1"), new OnlinePlayer(STEVE, "lobby-1"));
    }

    @Test
    void aStartingBackendsRosterInPartsAppliesAndIsAnsweredOnce() {
        RosterStamp stamp = new RosterStamp(1L, 1L);

        survival.track.remoteRoster("lobby-1", stamp, true, 1, 2, List.of(plain(ALEX)));
        survival.track.remoteRoster("lobby-1", stamp, true, 0, 2, List.of(plain(STEVE)));

        assertThat(survival.roster.onServer("lobby-1"))
                .containsExactly(new OnlinePlayer(ALEX, "lobby-1"), new OnlinePlayer(STEVE, "lobby-1"));
        assertThat(survival.out.sent).hasSize(1);
    }

    @Test
    void aRequestThatArrivesTwiceIsAnsweredOnce() {
        lobby.track.announce(true);
        Consumer<TrackNetworkPresence> request = lobby.out.sent.get(0);

        request.accept(survival.track);
        request.accept(survival.track);

        assertThat(survival.out.sent).hasSize(1);
    }

    @Test
    void aLostQuitIsRepairedByTheNextHeartbeat() {
        lobby.track.joined(STEVE);
        lobby.out.deliverAll(survival.track);

        lobby.track.left(STEVE);
        lobby.out.drop();
        lobby.track.announce(false);
        lobby.out.deliverAll(survival.track);

        assertThat(survival.roster.find(STEVE.uuid())).isEmpty();
    }

    @Test
    void anOlderRosterArrivingAfterANewerJoinDoesNotUndoIt() {
        lobby.track.announce(false);
        lobby.track.joined(STEVE);

        lobby.out.deliverReversed(survival.track);

        assertThat(survival.roster.find(STEVE.uuid())).contains(new OnlinePlayer(STEVE, "lobby-1"));
    }

    @Test
    void aRestartedBackendReplacesWhatItAnnouncedBefore() {
        lobby.track.joined(STEVE);
        lobby.track.joined(ALEX);
        lobby.out.deliverAll(survival.track);

        clock.advance(Duration.ofSeconds(5));
        Backend restarted = new Backend("lobby-1");
        restarted.track.joined(ALEX);
        restarted.out.deliverAll(survival.track);
        restarted.track.announce(true);
        restarted.out.deliverAll(survival.track);

        assertThat(survival.roster.onServer("lobby-1")).containsExactly(new OnlinePlayer(ALEX, "lobby-1"));
    }

    @Test
    void aBackendThatStopsWithoutQuittingItsPlayersDropsThemAfterTheLivenessWindow() {
        lobby.track.joined(STEVE);
        lobby.out.deliverAll(survival.track);

        clock.advance(LIVENESS);
        assertThat(survival.roster.isOnline(STEVE.uuid())).isTrue();
        clock.advance(Duration.ofSeconds(1));

        assertThat(survival.roster.isOnline(STEVE.uuid())).isFalse();
        assertThat(survival.roster.online()).isEmpty();
    }

    @Test
    void aBackendWithNoPlayersLeftStaysLiveUntilItIsNoLongerHeardFrom() {
        lobby.track.joined(STEVE);
        lobby.track.left(STEVE);
        lobby.out.deliverAll(survival.track);

        clock.advance(LIVENESS);
        assertThat(survival.roster.isLive("lobby-1")).isTrue();
        assertThat(survival.roster.isLive("hub-1")).isFalse();
        clock.advance(Duration.ofSeconds(1));

        assertThat(survival.roster.isLive("lobby-1")).isFalse();
        assertThat(survival.roster.isLive("survival-1")).isTrue();
    }

    @Test
    void heartbeatsKeepABackendsPlayersOnline() {
        lobby.track.joined(STEVE);
        lobby.out.deliverAll(survival.track);

        for (int beat = 0; beat < 10; beat++) {
            clock.advance(HEARTBEAT);
            survival.track.remoteAlive("lobby-1");
            lobby.track.announce(false);
            lobby.out.deliverAll(survival.track);
        }

        assertThat(survival.roster.isOnline(STEVE.uuid())).isTrue();
    }

    @Test
    void heartbeatsAloneDoNotKeepARosterFromAnEarlierRunAlive() {
        survival.track.remoteJoined("lobby-1", new RosterStamp(2_000L, 1L), STEVE, PresenceTraits.PLAIN);

        // Restarted with its clock set back: every roster of the new run reads as older than the run it replaces.
        for (int beat = 0; beat < 10; beat++) {
            clock.advance(HEARTBEAT);
            survival.track.remoteAlive("lobby-1");
            survival.track.remoteRoster(
                    "lobby-1", new RosterStamp(1_000L, beat + 1L), false, 0, 1, List.of(plain(ALEX)));
        }

        assertThat(survival.roster.onServer("lobby-1")).containsExactly(new OnlinePlayer(ALEX, "lobby-1"));
    }

    @Test
    void aStartingBackendLearnsWhoIsOnlineFromThePeersItAsks() {
        survival.track.joined(ALEX);
        survival.out.drop();
        lobby.track.joined(STEVE);
        lobby.out.drop();

        lobby.track.announce(true);
        lobby.out.deliverAll(survival.track);
        survival.out.deliverAll(lobby.track);

        assertThat(lobby.roster.online())
                .containsExactly(new OnlinePlayer(STEVE, "lobby-1"), new OnlinePlayer(ALEX, "survival-1"));
    }

    @Test
    void aLocalPlayerWinsTheirNameOverAnotherBackendsPlayer() {
        PlayerRef otherSteve = new PlayerRef(UUID.randomUUID(), "steve");
        lobby.track.joined(otherSteve);
        lobby.out.deliverAll(survival.track);
        survival.track.joined(STEVE);

        assertThat(survival.roster.findByName("steve")).contains(new OnlinePlayer(STEVE, "survival-1"));
    }

    @Test
    void anAnnouncementCarryingThisBackendsOwnNameIsIgnored() {
        Backend impostor = new Backend("lobby-1");
        impostor.track.joined(ALEX);

        impostor.out.deliverAll(lobby.track);

        assertThat(lobby.roster.find(ALEX.uuid())).isEmpty();
    }

    private final class Backend {

        final Outbox out;
        final RecordingNames names = new RecordingNames();
        final NetworkRoster roster;
        final TrackNetworkPresence track;

        Backend(String name) {
            this.out = new Outbox(name);
            this.roster = new NetworkRoster(
                    name,
                    clock,
                    LIVENESS,
                    () -> nameCase,
                    wanted -> sharedNames.contains(wanted.toLowerCase(java.util.Locale.ROOT)));
            this.track = new TrackNetworkPresence(roster, out, names, clock);
        }
    }

    private static RosterMember plain(PlayerRef player) {
        return new RosterMember(player, PresenceTraits.PLAIN);
    }

    /** Holds what a backend announced until the test delivers, reorders or loses it. */
    private static final class Outbox implements NetworkPresenceBroadcast {

        private final String origin;
        private final List<Consumer<TrackNetworkPresence>> sent = new ArrayList<>();

        Outbox(String origin) {
            this.origin = origin;
        }

        @Override
        public void joined(RosterStamp stamp, PlayerRef player, PresenceTraits traits) {
            sent.add(peer -> peer.remoteJoined(origin, stamp, player, traits));
        }

        @Override
        public void left(RosterStamp stamp, PlayerRef player) {
            sent.add(peer -> peer.remoteLeft(origin, stamp, player));
        }

        @Override
        public void roster(RosterStamp stamp, boolean replyRequested, List<RosterMember> players) {
            List<RosterMember> copy = List.copyOf(players);
            sent.add(peer -> peer.remoteRoster(origin, stamp, replyRequested, 0, 1, copy));
        }

        void deliverAll(TrackNetworkPresence peer) {
            List<Consumer<TrackNetworkPresence>> frames = List.copyOf(sent);
            sent.clear();
            frames.forEach(frame -> frame.accept(peer));
        }

        void deliverReversed(TrackNetworkPresence peer) {
            List<Consumer<TrackNetworkPresence>> frames = new ArrayList<>(sent);
            sent.clear();
            for (int i = frames.size() - 1; i >= 0; i--) {
                frames.get(i).accept(peer);
            }
        }

        void drop() {
            sent.clear();
        }
    }

    private static final class RecordingNames implements PlayerNameIndex {

        private final Map<String, PlayerRef> byLowerName = new HashMap<>();

        @Override
        public List<PlayerRef> byName(String name) {
            PlayerRef found = byLowerName.get(name.toLowerCase(Locale.ROOT));
            return found == null ? List.of() : List.of(found);
        }

        @Override
        public void record(UUID uuid, String name) {
            learn(uuid, name);
        }

        @Override
        public void learn(UUID uuid, String name) {
            byLowerName.put(name.toLowerCase(Locale.ROOT), new PlayerRef(uuid, name));
        }
    }
}
