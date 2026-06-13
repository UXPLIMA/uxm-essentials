package com.uxplima.uxmessentials.vote.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.BroadcastVisibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link PdcBroadcastVisibility}: a fresh player receives broadcasts, a toggle
 * round-trips through the PDC, and an offline player is handled safely.
 */
class PdcBroadcastVisibilityTest {

    private ServerMock server;
    private Plugin plugin;
    private BroadcastVisibility visibility;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        visibility = new PdcBroadcastVisibility(plugin);
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaultReceivesBroadcasts() {
        PlayerRef ref = ref(player);

        // A fresh player with no PDC entry receives broadcasts.
        assertThat(visibility.receivesBroadcasts(ref)).isTrue();
    }

    @Test
    void toggleHidesBroadcasts() {
        PlayerRef ref = ref(player);

        boolean nowReceives = visibility.toggle(ref);

        // First toggle: was on, now hidden.
        assertThat(nowReceives).isFalse();
        assertThat(visibility.receivesBroadcasts(ref)).isFalse();
    }

    @Test
    void doubleToggleRestoresDefault() {
        PlayerRef ref = ref(player);

        visibility.toggle(ref); // hidden
        boolean nowReceives = visibility.toggle(ref); // back on

        assertThat(nowReceives).isTrue();
        assertThat(visibility.receivesBroadcasts(ref)).isTrue();
    }

    @Test
    void receivesBroadcastsForOfflinePlayerDefaultsToTrue() {
        // An offline player (UUID not on the server) cannot have their PDC read.
        PlayerRef ghost = new PlayerRef(java.util.UUID.randomUUID(), "Ghost");

        // Safe default: true (the broadcaster only targets online players anyway).
        assertThat(visibility.receivesBroadcasts(ghost)).isTrue();
    }

    @Test
    void toggleForOfflinePlayerReturnsTrueSafely() {
        PlayerRef ghost = new PlayerRef(java.util.UUID.randomUUID(), "Ghost");

        // Toggle for an offline player is a no-op and returns true.
        assertThat(visibility.toggle(ghost)).isTrue();
    }

    private static PlayerRef ref(PlayerMock p) {
        return new PlayerRef(p.getUniqueId(), p.getName());
    }
}
