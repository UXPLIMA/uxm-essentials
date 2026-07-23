package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.commandcontrol.domain.CommandRateLimiter;
import com.uxplima.uxmessentials.commandcontrol.domain.SpamAction;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the command-spam listener: with the limit at N, the N+1-th command in the window is actioned (BLOCK cancels
 * it and warns, KICK disconnects, WARN only nudges), staying under the limit is never actioned, and a holder of the
 * spam-bypass permission is never counted or actioned however fast they type. A fixed clock keeps every command in one
 * window so the sliding-window arithmetic is deterministic.
 */
class CommandSpamListenerTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.spam.bypass";

    /** A fixed millisecond clock so every command in a test lands in the same sliding window. */
    private static long fixedClock() {
        return 1_000L;
    }

    private Messages messages;
    private MessageSink sink;
    private Player player;

    @BeforeEach
    void setUp() {
        messages = mock(Messages.class);
        sink = mock(MessageSink.class);
        when(messages.resolve(any(), any(), any())).thenReturn("too fast");

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Steve");
        when(player.hasPermission(anyString())).thenReturn(false);
    }

    private CommandSpamListener listener(int maxPerWindow, SpamAction action) {
        CommandRateLimiter limiter = CommandRateLimiter.of(true, maxPerWindow, 10, action);
        return new CommandSpamListener(limiter, BYPASS, messages, sink, CommandSpamListenerTest::fixedClock);
    }

    private PlayerCommandPreprocessEvent send(CommandSpamListener listener, String command) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, command, null);
        listener.onCommand(event);
        return event;
    }

    @Test
    void theNPlusFirstCommandIsBlockedAndWarned() {
        CommandSpamListener listener = listener(2, SpamAction.BLOCK);

        assertThat(send(listener, "/a").isCancelled()).isFalse();
        assertThat(send(listener, "/b").isCancelled()).isFalse();
        // The third command in the window (limit is two) is cancelled and the player is told to slow down.
        assertThat(send(listener, "/c").isCancelled()).isTrue();
        verify(sink).deliver(any(), anyString());
    }

    @Test
    void stayingUnderTheLimitIsNeverActioned() {
        CommandSpamListener listener = listener(3, SpamAction.BLOCK);

        assertThat(send(listener, "/a").isCancelled()).isFalse();
        assertThat(send(listener, "/b").isCancelled()).isFalse();
        assertThat(send(listener, "/c").isCancelled()).isFalse();
        verify(sink, never()).deliver(any(), anyString());
    }

    @Test
    void theBypassPermissionExemptsFromTheCountAndTheAction() {
        when(player.hasPermission(BYPASS)).thenReturn(true);
        CommandSpamListener listener = listener(1, SpamAction.KICK);

        // Far more than the limit, but a bypass holder is never counted, never kicked, never cancelled.
        for (int i = 0; i < 10; i++) {
            assertThat(send(listener, "/a").isCancelled()).isFalse();
        }
        verify(player, never()).kick(any(Component.class));
        verify(sink, never()).deliver(any(), anyString());
    }

    @Test
    void theKickActionDisconnectsTheFloodingPlayer() {
        CommandSpamListener listener = listener(1, SpamAction.KICK);

        assertThat(send(listener, "/a").isCancelled()).isFalse();
        // The second command (limit is one) exceeds the limit and kicks the player.
        send(listener, "/b");
        verify(player).kick(any(Component.class));
    }

    @Test
    void theWarnActionLetsTheCommandRunButStillNudges() {
        CommandSpamListener listener = listener(1, SpamAction.WARN);

        assertThat(send(listener, "/a").isCancelled()).isFalse();
        PlayerCommandPreprocessEvent second = send(listener, "/b");
        // WARN never cancels the command, but it does deliver the nudge message.
        assertThat(second.isCancelled()).isFalse();
        verify(sink).deliver(any(), anyString());
    }
}
