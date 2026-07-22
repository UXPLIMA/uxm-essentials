package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.entity.Player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Pins the central vanish-aware roster helper that every shared player enumeration routes through (command
 * suggestions, the player-picker GUI, the online-players menu source): a viewer sees only the players their
 * {@code canSee} graph reveals, so a vanished player hidden from them is filtered out while they still see
 * themselves, and a console (or no-viewer) sender has no graph and sees everyone.
 */
class VisibleOnlinePlayersTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aViewerDoesNotSeeAPlayerHiddenFromThemButStillSeesThemselves() {
        PlayerMock viewer = server.addPlayer("Viewer");
        PlayerMock ghost = server.addPlayer("Ghost");
        viewer.hidePlayer(MockBukkit.createMockPlugin(), ghost); // the viewer's canSee graph drops the vanished ghost

        List<Player> visible = CommandSuggestions.visibleOnlinePlayers(viewer);

        assertThat(visible).contains(viewer).doesNotContain(ghost);
    }

    @Test
    void aViewerSeesAPlayerNotHiddenFromThem() {
        PlayerMock viewer = server.addPlayer("Viewer");
        PlayerMock other = server.addPlayer("Other");

        assertThat(CommandSuggestions.visibleOnlinePlayers(viewer)).contains(viewer, other);
    }

    @Test
    void theConsoleSeesEveryOnlinePlayerIncludingTheVanished() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock ghost = server.addPlayer("Ghost");
        alice.hidePlayer(MockBukkit.createMockPlugin(), ghost); // hidden from alice, but the console has no graph

        assertThat(CommandSuggestions.visibleOnlinePlayers(server.getConsoleSender()))
                .contains(alice, ghost);
    }

    @Test
    void aNoViewerSenderSeesEveryOnlinePlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock ghost = server.addPlayer("Ghost");
        alice.hidePlayer(MockBukkit.createMockPlugin(), ghost);

        assertThat(CommandSuggestions.visibleOnlinePlayers(null)).contains(alice, ghost);
    }
}
