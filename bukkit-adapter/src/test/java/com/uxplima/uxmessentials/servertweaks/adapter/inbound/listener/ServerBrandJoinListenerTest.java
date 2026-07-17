package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.servertweaks.adapter.outbound.ServerBrandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the F3-brand join listener through a real {@link PlayerJoinEvent}: when the tweak is on the
 * configured brand is delivered to the joiner via the {@link ServerBrandSender} seam, and when it is off the listener
 * is a strict no-op — the gate-off-means-nothing-happens guarantee the whole module rests on.
 */
class ServerBrandJoinListenerTest {

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
    void enabledSendsTheBrandOnJoin() {
        PlayerMock player = server.addPlayer("Steve");
        RecordingSender sender = new RecordingSender();
        ServerBrandJoinListener listener = new ServerBrandJoinListener(true, sender);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(sender.recipients).containsExactly(player);
    }

    @Test
    void disabledSendsNothing() {
        PlayerMock player = server.addPlayer("Steve");
        RecordingSender sender = new RecordingSender();
        ServerBrandJoinListener listener = new ServerBrandJoinListener(false, sender);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(sender.recipients).isEmpty();
    }

    /** A {@link ServerBrandSender} that records every player it was asked to send the brand to. */
    private static final class RecordingSender implements ServerBrandSender {
        private final List<Player> recipients = new ArrayList<>();

        @Override
        public void send(Player player) {
            recipients.add(player);
        }
    }
}
