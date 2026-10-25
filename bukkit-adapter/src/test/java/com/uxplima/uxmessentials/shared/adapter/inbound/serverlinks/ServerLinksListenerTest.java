package com.uxplima.uxmessentials.shared.adapter.inbound.serverlinks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

/** A player's own copy of the pause-menu links goes out when they join and when their client changes language. */
class ServerLinksListenerTest {

    private final List<Player> sent = new ArrayList<>();
    private final ServerLinksListener listener = new ServerLinksListener(sent::add);
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
}
