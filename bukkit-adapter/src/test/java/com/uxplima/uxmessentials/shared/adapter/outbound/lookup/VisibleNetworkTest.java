package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.networkpresence.NetworkPlayers;
import com.uxplima.uxmessentials.shared.application.networkpresence.NetworkRoster;
import com.uxplima.uxmessentials.shared.application.networkpresence.RosterStamp;
import com.uxplima.uxmessentials.shared.application.networkpresence.TrackNetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresenceBroadcast;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.PresenceTraits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A player on this backend is offered the players of the other backends they may see, the console every one of them,
 * and tab completion names both backends' players once each.
 */
class VisibleNetworkTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef HIDDEN = new PlayerRef(UUID.randomUUID(), "Hidden");

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

    private ServerMock server;
    private PlayerMock alex;
    private int alexSees;
    private VisibleNetwork network;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        alex = server.addPlayer("Alex");
        NetworkRoster roster =
                new NetworkRoster("survival-1", CLOCK, Duration.ofSeconds(90), () -> NameCase.INSENSITIVE);
        TrackNetworkPresence track = new TrackNetworkPresence(roster, NetworkPresenceBroadcast.NONE, NO_NAMES, CLOCK);
        track.remoteJoined("lobby-1", new RosterStamp(1L, 1L), STEVE, PresenceTraits.PLAIN);
        track.remoteJoined("lobby-1", new RosterStamp(1L, 2L), HIDDEN, new PresenceTraits(2, false));
        network = VisibleNetwork.of(new NetworkPlayers(roster), player -> alexSees);
        CommandSuggestions.network(network);
    }

    @AfterEach
    void stop() {
        CommandSuggestions.network(VisibleNetwork.NONE);
        MockBukkit.unmock();
    }

    @Test
    void aPlayerIsOfferedOnlyThePlayersElsewhereTheySee() {
        assertThat(network.namesElsewhere(alex)).containsExactly("Steve");

        alexSees = 2;

        assertThat(network.namesElsewhere(alex)).containsExactly("Hidden", "Steve");
    }

    @Test
    void theConsoleIsOfferedEveryPlayerElsewhere() {
        assertThat(network.namesElsewhere(server.getConsoleSender())).containsExactly("Hidden", "Steve");
        assertThat(network.everyoneElsewhere()).containsExactlyInAnyOrder(STEVE, HIDDEN);
    }

    @Test
    void tabCompletionNamesThePlayersHereAndElsewhereOnce() {
        assertThat(CommandSuggestions.visibleNetworkNames(alex)).containsExactly("Alex", "Steve");
    }

    @Test
    void offTheNetworkOnlyThePlayersHereAreNamed() {
        CommandSuggestions.network(VisibleNetwork.NONE);

        assertThat(CommandSuggestions.visibleNetworkNames(alex)).containsExactly("Alex");
        assertThat(VisibleNetwork.NONE.findElsewhere("Steve", alex)).isEmpty();
    }
}
