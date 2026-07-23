package com.uxplima.uxmessentials.velocity.commandcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.commandcontrol.domain.CommandRateLimiter;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.commandcontrol.domain.SpamAction;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the proxy execute gate and the command-spam guard on {@link CommandExecuteEvent}: a blacklisted
 * command is denied and an allowed one runs, a whitelist keeps only its listed commands, and the spam guard
 * trips on the {@code N + 1}-th command while a bypass holder is never counted. The events are real
 * {@link CommandExecuteEvent} instances with a mocked {@link Player} source, so the denied/allowed result is
 * observed directly without a live proxy.
 */
class CommandControlListenerTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.bypass";
    private static final String VIEW = "uxmessentials.commandcontrol.viewproxycommands";
    private static final String SPAM_BYPASS = "uxmessentials.commandcontrol.spam.bypass";
    private static final HidePolicy HIDE_OFF = HidePolicy.of(false, List.of(), VIEW);
    private static final ProxyCommandMessages SILENT = new ProxyCommandMessages("", "", "", "", "", "");

    private final AtomicLong clock = new AtomicLong(0L);
    private Player player;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(false);
    }

    private CommandControlListener listener(RuleSet rules, CommandRateLimiter limiter) {
        ProxyCommandTreeFilter filter = new ProxyCommandTreeFilter(rules, HIDE_OFF, true, true);
        return new CommandControlListener(
                rules, HIDE_OFF, filter, limiter, ProxyGroupSource.empty(), SILENT, true, false, true, clock::get);
    }

    private static RuleSet blacklist(String... denied) {
        return RuleSet.of(RuleMode.BLACKLIST, List.of(denied), Map.of(), BYPASS);
    }

    private static RuleSet whitelist(String... allowed) {
        return RuleSet.of(RuleMode.WHITELIST, List.of(allowed), Map.of(), BYPASS);
    }

    private static CommandRateLimiter spamOff() {
        return CommandRateLimiter.of(false, 40, 2, SpamAction.BLOCK);
    }

    private boolean allowed(CommandControlListener listener, String command) {
        CommandExecuteEvent event = new CommandExecuteEvent(player, command);
        listener.onCommandExecute(event);
        return event.getResult().isAllowed();
    }

    @Test
    void gateDeniesABlacklistedCommandAndAllowsTheRest() {
        CommandControlListener listener = listener(blacklist("glist"), spamOff());

        assertThat(allowed(listener, "glist lobby")).isFalse();
        assertThat(allowed(listener, "home")).isTrue();
    }

    @Test
    void gateAllowsAWhitelistedCommandAndDeniesTheRest() {
        CommandControlListener listener = listener(whitelist("server"), spamOff());

        assertThat(allowed(listener, "server lobby")).isTrue();
        assertThat(allowed(listener, "glist")).isFalse();
    }

    @Test
    void gateDeniesTheNamespacedFormOfADeniedCommand() {
        CommandControlListener listener = listener(blacklist("glist"), spamOff());

        assertThat(allowed(listener, "velocity:glist")).isFalse();
    }

    @Test
    void spamTripsAfterTheLimitIsExceeded() {
        CommandRateLimiter limiter = CommandRateLimiter.of(true, 2, 60, SpamAction.BLOCK);
        CommandControlListener listener = listener(blacklist(), limiter);

        assertThat(allowed(listener, "home")).isTrue();
        assertThat(allowed(listener, "home")).isTrue();
        assertThat(allowed(listener, "home")).isFalse();
    }

    @Test
    void spamBypassHolderIsNeverCounted() {
        when(player.hasPermission(SPAM_BYPASS)).thenReturn(true);
        CommandRateLimiter limiter = CommandRateLimiter.of(true, 2, 60, SpamAction.BLOCK);
        CommandControlListener listener = listener(blacklist(), limiter);

        for (int i = 0; i < 10; i++) {
            assertThat(allowed(listener, "home")).isTrue();
        }
    }
}
