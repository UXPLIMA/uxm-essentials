package com.uxplima.uxmessentials.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.communication.domain.ServerSwitchPolicy;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * A player moving between the servers of a network shows no join or quit line, while one joining or leaving the
 * network does; {@code {count}} counts this server or the whole network.
 */
class ServerSwitchesTest {

    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    /** A Bedrock player through Floodgate: the Xbox user id under a zero high half, the name prefixed with a dot. */
    private static final PlayerRef BEDROCK =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6"), ".BedrockGamer123");

    private final Presence presence = new Presence();
    private ServerSwitchPolicy policy = ServerSwitchPolicy.DEFAULT;
    private boolean networked = true;
    private final ServerSwitches switches = new ServerSwitches(presence, () -> policy, () -> networked);

    @Test
    void aPlayerArrivingFromAnotherServerOfTheNetworkJoinsSilently() {
        presence.cameFrom.put(STEVE.uuid(), "lobby-1");

        assertThat(switches.hidesJoin(STEVE.uuid())).isTrue();
    }

    @Test
    void aPlayerNewToTheNetworkJoinsWithALine() {
        assertThat(switches.hidesJoin(STEVE.uuid())).isFalse();
    }

    @Test
    void aBedrockPlayerMovingBetweenServersJoinsSilentlyToo() {
        presence.cameFrom.put(BEDROCK.uuid(), "lobby-1");

        assertThat(switches.hidesJoin(BEDROCK.uuid())).isTrue();
    }

    @Test
    void withHidingOffOrOnASingleServerEveryJoinHasItsLineAndNoQuitWaits() {
        presence.cameFrom.put(STEVE.uuid(), "lobby-1");

        policy = new ServerSwitchPolicy(false, Duration.ofSeconds(3), false);
        assertThat(switches.hidesJoin(STEVE.uuid())).isFalse();
        assertThat(switches.holdsQuit()).isFalse();

        policy = ServerSwitchPolicy.DEFAULT;
        networked = false;
        assertThat(switches.hidesJoin(STEVE.uuid())).isFalse();
        assertThat(switches.holdsQuit()).isFalse();
    }

    @Test
    void aQuitWaitsTheWindowAndShowsOnlyWhenThePlayerTurnedUpNowhere() {
        assertThat(switches.holdsQuit()).isTrue();
        assertThat(switches.quitWait()).isEqualTo(Duration.ofSeconds(3));

        assertThat(switches.showsHeldQuit(STEVE.uuid())).isTrue();
        presence.online.add(new OnlinePlayer(STEVE, "survival-1"));
        assertThat(switches.showsHeldQuit(STEVE.uuid())).isFalse();
    }

    @Test
    void aZeroWindowShowsAQuitAtOnce() {
        policy = new ServerSwitchPolicy(true, Duration.ZERO, false);

        assertThat(switches.holdsQuit()).isFalse();
    }

    @Test
    void theCountIsThisServersUnlessTheNetworkIsCounted() {
        presence.online.add(new OnlinePlayer(STEVE, "lobby-1"));
        presence.online.add(new OnlinePlayer(new PlayerRef(UUID.randomUUID(), "Alex"), "survival-1"));
        PlayerRef joining = new PlayerRef(UUID.randomUUID(), "steve");

        assertThat(switches.count(1, joining.uuid(), true)).isEqualTo(1);

        policy = new ServerSwitchPolicy(true, Duration.ofSeconds(3), true);
        // An offline-mode case variant is its own account and counts apart from Steve.
        assertThat(switches.count(1, joining.uuid(), true)).isEqualTo(3);
        assertThat(switches.count(0, STEVE.uuid(), false)).isEqualTo(1);

        networked = false;
        assertThat(switches.count(1, joining.uuid(), true)).isEqualTo(1);
    }

    @Test
    void aPlayerTheRosterStillListsOnTwoServersIsCountedOnce() {
        policy = new ServerSwitchPolicy(true, Duration.ofSeconds(3), true);
        presence.online.add(new OnlinePlayer(STEVE, "lobby-1"));
        presence.online.add(new OnlinePlayer(STEVE, "survival-1"));

        assertThat(switches.count(1, STEVE.uuid(), true)).isEqualTo(1);
    }

    /** The network as this server heard it: who is online where, and where a joining player came from. */
    private static final class Presence implements NetworkPresence {

        final List<OnlinePlayer> online = new ArrayList<>();
        final Map<UUID, String> cameFrom = new HashMap<>();

        @Override
        public String localServer() {
            return "survival-1";
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return online.stream()
                    .filter(entry -> entry.player().uuid().equals(player))
                    .findFirst();
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return online.stream()
                    .filter(entry -> entry.player().name().equals(name))
                    .findFirst();
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return online.stream()
                    .filter(entry -> entry.server().equals(server))
                    .toList();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.copyOf(online);
        }

        @Override
        public Optional<String> cameFrom(UUID player, Duration within) {
            return Optional.ofNullable(cameFrom.get(player));
        }
    }
}
