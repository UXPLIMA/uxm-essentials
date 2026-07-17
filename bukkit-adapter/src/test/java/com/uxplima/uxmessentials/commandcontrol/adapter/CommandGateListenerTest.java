package com.uxplima.uxmessentials.commandcontrol.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandGateListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.application.CommandControlMessageKey;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the command whitelist/blacklist gate listener: a whitelist allows only its listed commands and denies the
 * rest, a blacklist denies its listed commands and allows the rest, a player's group selects its own list, a
 * {@code .bypass} holder is never gated, the denied command is cancelled and yields the configured deny message, and
 * the command root is extracted from the raw message (arguments dropped, case-insensitive). {@link Messages} and
 * {@link MessageSink} are mocked so the cancel decision and delivered key are observable without a live server.
 */
class CommandGateListenerTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.bypass";

    private Messages messages;
    private MessageSink sink;
    private Player player;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        messages = mock(Messages.class);
        sink = mock(MessageSink.class);
        when(messages.resolve(any(), any(), any())).thenReturn("denied");

        uuid = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Steve");
        when(player.hasPermission(anyString())).thenReturn(false);
    }

    private CommandGateListener listener(RuleSet rules, PlayerGroupSource groups) {
        return new CommandGateListener(
                rules, groups, messages, sink, CommandControlMessageKey.COMMANDCONTROL_UNKNOWN_COMMAND);
    }

    private static PlayerGroupSource inGroup(String group) {
        return who -> Optional.of(group);
    }

    private static PlayerGroupSource noGroup() {
        return who -> Optional.empty();
    }

    @Test
    void whitelistAllowsOnlyListedAndDeniesTheRest() {
        CommandGateListener listener =
                listener(RuleSet.of(RuleMode.WHITELIST, List.of("home"), Map.of(), BYPASS), noGroup());

        PlayerCommandPreprocessEvent allowed = new PlayerCommandPreprocessEvent(player, "/home", null);
        listener.onCommand(allowed);
        assertThat(allowed.isCancelled()).isFalse();

        PlayerCommandPreprocessEvent denied = new PlayerCommandPreprocessEvent(player, "/op Steve", null);
        listener.onCommand(denied);
        assertThat(denied.isCancelled()).isTrue();
    }

    @Test
    void blacklistDeniesListedAndAllowsTheRest() {
        CommandGateListener listener =
                listener(RuleSet.of(RuleMode.BLACKLIST, List.of("op"), Map.of(), BYPASS), noGroup());

        PlayerCommandPreprocessEvent denied = new PlayerCommandPreprocessEvent(player, "/op", null);
        listener.onCommand(denied);
        assertThat(denied.isCancelled()).isTrue();

        PlayerCommandPreprocessEvent allowed = new PlayerCommandPreprocessEvent(player, "/home", null);
        listener.onCommand(allowed);
        assertThat(allowed.isCancelled()).isFalse();
    }

    @Test
    void aGroupSelectsItsOwnList() {
        RuleSet rules = RuleSet.of(RuleMode.WHITELIST, List.of("spawn"), Map.of("vip", List.of("fly")), BYPASS);

        // A vip player is governed by the vip whitelist: fly allowed, the default-only spawn denied.
        CommandGateListener vipGate = listener(rules, inGroup("vip"));
        PlayerCommandPreprocessEvent fly = new PlayerCommandPreprocessEvent(player, "/fly", null);
        vipGate.onCommand(fly);
        assertThat(fly.isCancelled()).isFalse();

        PlayerCommandPreprocessEvent spawn = new PlayerCommandPreprocessEvent(player, "/spawn", null);
        vipGate.onCommand(spawn);
        assertThat(spawn.isCancelled()).isTrue();

        // A player in a group with no list falls through to the default whitelist: spawn allowed, fly denied.
        CommandGateListener memberGate = listener(rules, inGroup("member"));
        PlayerCommandPreprocessEvent memberSpawn = new PlayerCommandPreprocessEvent(player, "/spawn", null);
        memberGate.onCommand(memberSpawn);
        assertThat(memberSpawn.isCancelled()).isFalse();
    }

    @Test
    void bypassHolderIsNeverGated() {
        when(player.hasPermission(BYPASS)).thenReturn(true);
        CommandGateListener listener = listener(RuleSet.of(RuleMode.WHITELIST, List.of(), Map.of(), BYPASS), noGroup());

        // An empty whitelist denies everyone — but not the bypass holder.
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/op", null);
        listener.onCommand(event);

        assertThat(event.isCancelled()).isFalse();
        verify(sink, never()).deliver(any(), anyString());
    }

    @Test
    void aDeniedCommandIsCancelledAndYieldsTheConfiguredMessage() {
        CommandGateListener listener =
                listener(RuleSet.of(RuleMode.BLACKLIST, List.of("op"), Map.of(), BYPASS), noGroup());

        // Case and arguments are handled: /OP with an argument still matches the "op" root.
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/OP Steve", null);
        listener.onCommand(event);

        assertThat(event.isCancelled()).isTrue();
        verify(messages)
                .resolve(any(PlayerRef.class), eq(CommandControlMessageKey.COMMANDCONTROL_UNKNOWN_COMMAND), any());
        verify(sink).deliver(any(PlayerRef.class), eq("denied"));
    }

    @Test
    void anInertBlacklistShortCircuitsWithoutReadingThePlayer() {
        CommandGateListener listener = listener(RuleSet.of(RuleMode.BLACKLIST, List.of(), Map.of(), BYPASS), who -> {
            throw new AssertionError("group source must not be read when the rule set is inert");
        });

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/op", null);
        listener.onCommand(event);

        assertThat(event.isCancelled()).isFalse();
        verify(sink, never()).deliver(any(), anyString());
    }
}
