package com.uxplima.uxmessentials.shared.application.networkpresence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresenceBroadcast;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.PresenceTraits;
import com.uxplima.uxmessentials.testing.MutableClock;
import org.junit.jupiter.api.Test;

/**
 * The lists that name the players online show the players of the whole network a viewer may see: a player vanished on
 * another backend only to a viewer who sees that deep, a player whose backend said nothing only to one who sees every
 * level, and the staff of every backend.
 */
class NetworkPlayersTest {

    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final PlayerRef HELPER = new PlayerRef(UUID.randomUUID(), "Helper");
    /** A Bedrock player through Floodgate: the Xbox user id under a zero high half, the name prefixed with a dot. */
    private static final PlayerRef BEDROCK =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6"), ".BedrockGamer123");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
    private NameCase nameCase = NameCase.INSENSITIVE;
    private final NetworkRoster roster = new NetworkRoster("survival-1", clock, Duration.ofSeconds(90), () -> nameCase);
    private final List<String> announced = new ArrayList<>();
    private final TrackNetworkPresence track = new TrackNetworkPresence(roster, new Recording(), new NoNames(), clock);
    private final NetworkPlayers players = new NetworkPlayers(roster, () -> nameCase);
    private long sequence;

    @Test
    void offlineModeTheNameSpelledExactlyIsFoundElsewhereAndAnotherSpellingIsNot() {
        nameCase = NameCase.SENSITIVE;
        PlayerRef lowerBob = offline("bob");
        PlayerRef bob = offline("Bob");
        remote("lobby-1", lowerBob, PresenceTraits.PLAIN);
        remote("lobby-2", bob, PresenceTraits.PLAIN);

        assertThat(players.findElsewhere("Bob", 0)).map(OnlinePlayer::player).contains(bob);
        assertThat(players.findElsewhere("bob", 0)).map(OnlinePlayer::player).contains(lowerBob);
        assertThat(players.findElsewhere("BOB", 0)).isEmpty();
    }

    @Test
    void offlineModeALoneVariantInAnotherCaseIsNotTakenForTheNameTyped() {
        nameCase = NameCase.SENSITIVE;
        remote("lobby-1", offline("bob"), PresenceTraits.PLAIN);

        assertThat(players.findElsewhere("Bob", 0)).isEmpty();
    }

    @Test
    void onlineModeTheExactSpellingElsewhereWinsOverAVariantElsewhere() {
        PlayerRef lowerBob = offline("bob");
        PlayerRef bob = offline("Bob");
        remote("lobby-1", lowerBob, PresenceTraits.PLAIN);
        remote("lobby-2", bob, PresenceTraits.PLAIN);

        assertThat(players.findElsewhere("Bob", 0)).map(OnlinePlayer::player).contains(bob);
        assertThat(players.findElsewhere("bob", 0)).map(OnlinePlayer::player).contains(lowerBob);
    }

    @Test
    void onlineModeAPlayerHereHoldsTheirNameSoNobodyElsewhereAnswersForIt() {
        track.joined(offline("bob"));
        remote("lobby-1", offline("Bob"), PresenceTraits.PLAIN);

        assertThat(players.findElsewhere("Bob", 0)).isEmpty();
    }

    @Test
    void offlineModeAVariantHereDoesNotHideTheExactSpellingElsewhere() {
        nameCase = NameCase.SENSITIVE;
        PlayerRef bob = offline("Bob");
        track.joined(offline("bob"));
        remote("lobby-1", bob, PresenceTraits.PLAIN);

        assertThat(players.findElsewhere("Bob", 0)).map(OnlinePlayer::player).contains(bob);
    }

    @Test
    void onlineModeALoneVariantElsewhereIsFoundAndSeveralAreNot() {
        PlayerRef bob = offline("Bob");
        remote("lobby-1", bob, PresenceTraits.PLAIN);
        assertThat(players.findElsewhere("BOB", 0)).map(OnlinePlayer::player).contains(bob);

        remote("lobby-2", offline("bob"), PresenceTraits.PLAIN);

        assertThat(players.findElsewhere("BOB", 0)).isEmpty();
    }

    @Test
    void anExactSpellingHiddenFromTheViewerIsNotAnsweredWithAVariant() {
        nameCase = NameCase.SENSITIVE;
        remote("lobby-1", offline("Bob"), new PresenceTraits(3, false));
        remote("lobby-1", offline("bob"), PresenceTraits.PLAIN);

        assertThat(players.findElsewhere("Bob", 0)).isEmpty();
    }

    @Test
    void playersOnOtherBackendsAreListedButNotThoseHere() {
        track.joined(ALEX);
        remote("lobby-1", STEVE, PresenceTraits.PLAIN);
        remote("lobby-1", BEDROCK, PresenceTraits.PLAIN);

        assertThat(players.elsewhere(0))
                .extracting(online -> online.player().name())
                .containsExactly(".BedrockGamer123", "Steve");
    }

    @Test
    void aPlayerVanishedElsewhereIsListedOnlyForAViewerWhoSeesThatDeep() {
        remote("lobby-1", STEVE, new PresenceTraits(2, false));

        assertThat(players.elsewhere(0)).isEmpty();
        assertThat(players.elsewhere(1)).isEmpty();
        assertThat(players.elsewhere(2)).extracting(OnlinePlayer::player).containsExactly(STEVE);
        assertThat(players.elsewhere(NetworkPlayers.SEES_ALL)).hasSize(1);
    }

    @Test
    void aPlayerWhoseBackendSaidNothingIsSeenOnlyByAViewerWhoSeesEveryLevel() {
        remote("lobby-1", STEVE, PresenceTraits.UNKNOWN);

        assertThat(players.elsewhere(5)).isEmpty();
        assertThat(players.elsewhere(PresenceTraits.UNKNOWN_LEVEL)).hasSize(1);
        assertThat(players.findElsewhere("Steve", 5)).isEmpty();
    }

    @Test
    void aPlayerWhoVanishesLaterDropsOutOfTheList() {
        remote("lobby-1", STEVE, PresenceTraits.PLAIN);
        assertThat(players.elsewhere(0)).hasSize(1);

        remote("lobby-1", STEVE, new PresenceTraits(1, false));

        assertThat(players.elsewhere(0)).isEmpty();
        assertThat(roster.find(STEVE.uuid())).map(OnlinePlayer::server).contains("lobby-1");
    }

    @Test
    void theStaffOfEveryBackendAreListedAndCountedHiddenOnesTooInTheCount() {
        remote("lobby-1", HELPER, new PresenceTraits(0, true));
        remote("lobby-1", STEVE, new PresenceTraits(3, true));
        remote("hub-1", ALEX, PresenceTraits.PLAIN);

        assertThat(players.staffElsewhere(0)).extracting(OnlinePlayer::player).containsExactly(HELPER);
        assertThat(players.staffElsewhere(3)).extracting(OnlinePlayer::player).containsExactly(HELPER, STEVE);
        assertThat(players.staffElsewhereCount()).isEqualTo(2);
    }

    @Test
    void aNameIsFoundOnAnotherBackendOnlyWhenTheViewerSeesThePlayer() {
        remote("lobby-1", STEVE, new PresenceTraits(2, false));
        remote("lobby-1", BEDROCK, PresenceTraits.PLAIN);

        assertThat(players.findElsewhere("steve", 0)).isEmpty();
        assertThat(players.findElsewhere("steve", 2)).map(OnlinePlayer::server).contains("lobby-1");
        assertThat(players.findElsewhere(".bedrockgamer123", 0)).isPresent();
    }

    @Test
    void anOfflineModeCaseVariantElsewhereIsItsOwnAccountInTheList() {
        PlayerRef lowerSteve =
                new PlayerRef(UUID.nameUUIDFromBytes("OfflinePlayer:steve".getBytes(StandardCharsets.UTF_8)), "steve");
        remote("lobby-1", STEVE, PresenceTraits.PLAIN);
        remote("hub-1", lowerSteve, PresenceTraits.PLAIN);

        assertThat(players.elsewhere(0)).extracting(OnlinePlayer::player).containsExactlyInAnyOrder(STEVE, lowerSteve);
    }

    @Test
    void thisBackendAnnouncesATraitChangeOnceAndAJoinWithItsTraits() {
        PresenceTraits[] current = {PresenceTraits.PLAIN};
        track.readTraitsWith(player -> current[0]);
        track.joined(ALEX);
        current[0] = new PresenceTraits(1, true);

        track.retrait(ALEX.uuid());
        track.retrait(ALEX.uuid());
        track.retrait(STEVE.uuid());

        assertThat(announced).containsExactly("Alex " + PresenceTraits.PLAIN, "Alex " + new PresenceTraits(1, true));
        assertThat(roster.traits(ALEX.uuid())).contains(new PresenceTraits(1, true));
    }

    @Test
    void theHeartbeatCarriesTraitsNobodyReported() {
        PresenceTraits[] current = {PresenceTraits.PLAIN};
        track.readTraitsWith(player -> current[0]);
        track.joined(ALEX);
        current[0] = new PresenceTraits(0, true);

        track.announce(false);

        assertThat(announced).last().isEqualTo("roster [Alex " + new PresenceTraits(0, true) + "]");
    }

    private void remote(String server, PlayerRef player, PresenceTraits traits) {
        sequence++;
        track.remoteJoined(server, new RosterStamp(1L, sequence), player, traits);
    }

    /** What this backend announced, one line per frame. */
    private final class Recording implements NetworkPresenceBroadcast {

        @Override
        public void joined(RosterStamp stamp, PlayerRef player, PresenceTraits traits) {
            announced.add(player.name() + " " + traits);
        }

        @Override
        public void left(RosterStamp stamp, PlayerRef player) {
            announced.add(player.name() + " left");
        }

        @Override
        public void roster(RosterStamp stamp, boolean replyRequested, List<RosterMember> members) {
            announced.add("roster "
                    + members.stream()
                            .map(member -> member.player().name() + " " + member.traits())
                            .toList());
        }
    }

    /** A name index that learns nothing; only presence is under test. */
    private static final class NoNames implements PlayerNameIndex {

        @Override
        public List<PlayerRef> byName(String name) {
            return List.of();
        }

        @Override
        public void learn(UUID uuid, String name) {}

        @Override
        public void record(UUID uuid, String name) {}
    }

    /** An offline-mode account: its uuid derived from the name, so each spelling is its own account. */
    private static PlayerRef offline(String name) {
        return new PlayerRef(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
    }
}
