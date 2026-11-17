package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.networkpresence.NetworkRoster;
import com.uxplima.uxmessentials.shared.application.networkpresence.TrackNetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.junit.jupiter.api.Test;

/** Joins, quits and rosters on one backend reach another backend's presence through the bus wire format. */
class NetworkPresenceSyncTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
    private static final Duration LIVENESS = Duration.ofSeconds(90);
    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");

    private static final PlayerNameIndex NO_NAMES = new PlayerNameIndex() {
        @Override
        public List<PlayerRef> byName(String name) {
            return List.of();
        }

        @Override
        public void record(UUID uuid, String name) {}

        @Override
        public void learn(UUID uuid, String name) {}
    };

    private final NetworkRoster survival = new NetworkRoster("survival-1", CLOCK, LIVENESS);
    private final Wire lobbyToSurvival = new Wire(
            "lobby-1",
            NetworkPresenceSync.listener(new TrackNetworkPresence(
                    survival, NetworkPresenceSync.publisher(new Wire("survival-1", message -> {})), NO_NAMES, CLOCK)));
    private final TrackNetworkPresence lobby = new TrackNetworkPresence(
            new NetworkRoster("lobby-1", CLOCK, LIVENESS),
            NetworkPresenceSync.publisher(lobbyToSurvival),
            NO_NAMES,
            CLOCK);

    @Test
    void aJoinAndAQuitCrossTheBus() {
        lobby.joined(STEVE);
        assertThat(survival.findByName("steve")).contains(new OnlinePlayer(STEVE, "lobby-1"));

        lobby.left(STEVE);
        assertThat(survival.find(STEVE.uuid())).isEmpty();
    }

    @Test
    void aRosterCrossesTheBusAndRepairsAMissedJoin() {
        lobbyToSurvival.down = true;
        lobby.joined(STEVE);
        lobbyToSurvival.down = false;

        lobby.announce(false);

        assertThat(survival.online()).containsExactly(new OnlinePlayer(STEVE, "lobby-1"));
    }

    @Test
    void aRosterTooLargeForOneFrameCrossesInPartsThatCanBeRelayed() {
        List<OnlinePlayer> expected = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            PlayerRef player = new PlayerRef(UUID.randomUUID(), String.format(Locale.ROOT, "Player%010d", i));
            lobby.joined(player);
            expected.add(new OnlinePlayer(player, "lobby-1"));
        }
        lobbyToSurvival.largest = 0;

        lobby.announce(false);

        assertThat(lobbyToSurvival.largest).isLessThanOrEqualTo(BusChannel.MAX_FRAME_BYTES);
        assertThat(survival.onServer("lobby-1")).containsExactlyInAnyOrderElementsOf(expected);
    }

    /** Encodes each published frame and hands the decoded copy to the receiving backend, as the transport would. */
    private static final class Wire implements BusPublisher {

        private final String serverId;
        private final RemoteSyncListener receiver;
        boolean down;
        int largest;

        Wire(String serverId, RemoteSyncListener receiver) {
            this.serverId = serverId;
            this.receiver = receiver;
        }

        @Override
        public void publish(NetworkMessage message) {
            byte[] frame = NetworkMessageCodec.encode(message);
            largest = Math.max(largest, frame.length);
            if (!down) {
                receiver.onRemoteChange(NetworkMessageCodec.decode(frame));
            }
        }

        @Override
        public String serverId() {
            return serverId;
        }
    }
}
