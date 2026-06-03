package com.uxplima.uxmessentials.communication.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.ChatLockListener;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.application.CommunicationNotifier;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The {@code /togglechat} chat lock: while the {@link ChatLock} is set the {@link ChatLockListener} cancels a
 * non-bypass player's {@code AsyncChatEvent} and tells them chat is locked; a player with the bypass node still
 * speaks; once the lock is flipped off chat flows again. The event is a Mockito double (so no real chat
 * pipeline is needed), with a real {@code PlayerMock} for the permission check.
 */
class ChatLockTest {

    private static final String BYPASS = "uxmessentials.communication.chat.bypass";

    private ServerMock server;
    private RecordingSink sink;
    private ChatLock lock;
    private ChatLockListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        sink = new RecordingSink();
        lock = new ChatLock();
        listener = new ChatLockListener(lock, new CommunicationNotifier(new KeyMessages(), sink));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aLockedChatCancelsANonBypassPlayerAndTellsThemItIsLocked() {
        PlayerMock speaker = server.addPlayer("Alice");
        lock.toggle(); // lock on
        AsyncChatEvent event = chatEvent(speaker);

        listener.onChat(event);

        verify(event).setCancelled(true);
        assertThat(sink.keys).contains(CommunicationMessageKey.CHAT_LOCKED);
    }

    @Test
    void aBypassPlayerStillSpeaksWhileChatIsLocked() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), BYPASS, true);
        lock.toggle(); // lock on
        AsyncChatEvent event = chatEvent(staff);

        listener.onChat(event);

        verify(event, never()).setCancelled(true);
        assertThat(sink.keys).doesNotContain(CommunicationMessageKey.CHAT_LOCKED);
    }

    @Test
    void chatFlowsWhenTheLockIsOff() {
        PlayerMock speaker = server.addPlayer("Bob");
        // lock left off
        AsyncChatEvent event = chatEvent(speaker);

        listener.onChat(event);

        verify(event, never()).setCancelled(true);
        assertThat(sink.keys).isEmpty();
    }

    private AsyncChatEvent chatEvent(PlayerMock speaker) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(speaker);
        return event;
    }

    /** Records each delivered key so the listener's notice is observable. */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // the key is captured in KeyMessages; renderedText is the key string
        }
    }

    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }
}
