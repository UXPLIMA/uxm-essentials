package com.uxplima.uxmessentials.shared.adapter.inbound.locale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.message.ClientLocales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The language a player's client reads is known from the moment they join until the moment they leave.
 *
 * <p>It is read here, on the thread that owns the player, and kept where any thread can read it: a line written
 * after a database read has no player to ask, and it used to fall back to the server's default.
 */
class ClientLocaleListenerTest {

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    private ServerMock server;
    private ClientLocales clients;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        clients = new ClientLocales();
        server.getPluginManager()
                .registerEvents(new ClientLocaleListener(clients), MockBukkit.createMockPlugin("uxmEssentials"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a player who joins is known in the language their client reads")
    void aJoiningPlayerIsKnown() {
        PlayerMock ada = server.addPlayer("Ada");

        assertThat(clients.of(ada.getUniqueId())).contains(ada.locale());
    }

    @Test
    @DisplayName("a player who switches their client's language is known in the new one")
    void aSwitchIsFollowed() {
        PlayerMock ada = server.addPlayer("Ada");

        server.getPluginManager().callEvent(new PlayerLocaleChangeEvent(ada, "tr_tr"));

        assertThat(clients.of(ada.getUniqueId())).contains(TURKISH);
    }

    @Test
    @DisplayName("a player who leaves is forgotten")
    void aLeavingPlayerIsForgotten() {
        PlayerMock ada = server.addPlayer("Ada");

        server.getPluginManager()
                .callEvent(new PlayerQuitEvent(ada, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(clients.of(ada.getUniqueId())).isEmpty();
    }
}
