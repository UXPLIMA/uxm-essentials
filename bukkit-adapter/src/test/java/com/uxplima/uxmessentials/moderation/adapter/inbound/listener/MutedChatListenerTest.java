package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A muted player does not speak in public chat.
 *
 * <p>Nothing stopped it. {@code /msg}, {@code /me} and the rest were refused to a muted player, and the chat line
 * itself went to everybody: on a live server a player muted for spamming went on talking in chat.
 */
class MutedChatListenerTest {

    private static final ChatRenderer AS_IS = (source, displayName, message, viewer) -> message;

    private ServerMock server;
    private final Set<UUID> muted = new HashSet<>();
    private final Set<UUID> exempt = new HashSet<>();
    private final List<String> told = new ArrayList<>();
    private MutedChatListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Messages messages = (viewer, key, values) -> key.key();
        MessageSink sink = (viewer, text) -> told.add(viewer.name() + " " + text);
        listener = new MutedChatListener(
                who -> muted.contains(who.uuid()), who -> exempt.contains(who.uuid()), messages, sink);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a muted player's chat line is stopped, and the player is told why")
    void aMutedPlayerIsStopped() {
        PlayerMock bob = server.addPlayer("Bob");
        muted.add(bob.getUniqueId());
        AsyncChatEvent event = chat(bob, "still talking");

        listener.onChat(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(told).containsExactly("Bob moderation.muted-chat-blocked");
    }

    @Test
    @DisplayName("a player who is not muted, or is exempt, speaks as usual")
    void othersSpeak() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock carol = server.addPlayer("Carol");
        muted.add(carol.getUniqueId());
        exempt.add(carol.getUniqueId());
        AsyncChatEvent fromAlice = chat(alice, "hello");
        AsyncChatEvent fromCarol = chat(carol, "hello");

        listener.onChat(fromAlice);
        listener.onChat(fromCarol);

        assertThat(fromAlice.isCancelled()).isFalse();
        assertThat(fromCarol.isCancelled()).isFalse();
        assertThat(told).isEmpty();
    }

    private static AsyncChatEvent chat(PlayerMock speaker, String text) {
        Component message = Component.text(text);
        Set<Audience> viewers = Set.of(speaker);
        return new AsyncChatEvent(true, speaker, viewers, AS_IS, message, message, SignedMessage.system(text, message));
    }
}
