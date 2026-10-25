package com.uxplima.uxmessentials.shared.adapter.inbound.serverlinks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

/** A player's own copy of the pause-menu links goes out when they join and when their client changes language. */
class ServerLinksListenerTest {

    private final List<Player> sent = new ArrayList<>();
    private final List<UUID> forgotten = new ArrayList<>();
    private final ServerLinksListener listener = new ServerLinksListener(sent::add, forgotten::add);
    private final Player player = mock(Player.class);

    @Test
    void aJoiningPlayerIsSentTheirLinks() {
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(sent).containsExactly(player);
    }

    @Test
    void aClientThatChangesLanguageIsSentTheLinksAgain() {
        listener.onLocaleChange(new PlayerLocaleChangeEvent(player, "tr_tr"));

        assertThat(sent).containsExactly(player);
    }

    @Test
    void aPlayerWhoLeavesIsForgotten() {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(id);

        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(forgotten).containsExactly(id);
    }
}
