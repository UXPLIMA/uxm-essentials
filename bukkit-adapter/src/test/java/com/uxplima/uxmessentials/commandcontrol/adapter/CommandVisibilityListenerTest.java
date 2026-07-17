package com.uxplima.uxmessentials.commandcontrol.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandVisibilityListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.CommandPermissionView;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.application.CommandControlMessageKey;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the command-visibility listener: the tab-completion scrub removes the commands a player may not run (per the
 * rule set and the vanilla permission) and keeps the rest, the plugin-hide removes the plugin-listing commands and
 * their namespaced variants, a {@code .bypass} holder and a {@code .viewplugins} holder keep them, the scrub-help block
 * cancels a {@code /plugins} for a normal player and lets a permitted one through, the async completion filter clears
 * the arguments of a filtered command, and each config gate switched off is a no-op. {@link Messages} and
 * {@link MessageSink} are mocked so the cancel decision and delivered key are observable without a live server.
 */
class CommandVisibilityListenerTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.bypass";
    private static final String VIEW = "uxmessentials.commandcontrol.viewplugins";
    private static final List<String> PLUGIN_ROOTS = List.of("plugins", "pl", "help");

    private Messages messages;
    private MessageSink sink;
    private Player player;

    @BeforeEach
    void setUp() {
        messages = mock(Messages.class);
        sink = mock(MessageSink.class);
        when(messages.resolve(any(), any(), any())).thenReturn("hidden");

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Steve");
        when(player.hasPermission(anyString())).thenReturn(false);
    }

    private static final PlayerGroupSource NO_GROUP = who -> Optional.empty();

    private CommandVisibilityListener listener(
            RuleSet rules, HidePolicy hide, boolean tabOn, boolean scrub, CommandPermissionView perms) {
        return new CommandVisibilityListener(rules, hide, NO_GROUP, perms, tabOn, scrub, BYPASS, messages, sink);
    }

    private static RuleSet blacklist(String... denied) {
        return RuleSet.of(RuleMode.BLACKLIST, List.of(denied), Map.of(), BYPASS);
    }

    private static HidePolicy hideOff() {
        return HidePolicy.of(false, List.of(), VIEW);
    }

    private static HidePolicy hideOn() {
        return HidePolicy.of(true, PLUGIN_ROOTS, VIEW);
    }

    private List<String> sentAfterFilter(CommandVisibilityListener listener, String... commands) {
        PlayerCommandSendEvent event = new PlayerCommandSendEvent(player, new ArrayList<>(List.of(commands)));
        listener.onCommandSend(event);
        return new ArrayList<>(event.getCommands());
    }

    @Test
    void tabFilterRemovesDeniedCommandsAndKeepsAllowed() {
        CommandVisibilityListener listener =
                listener(blacklist("op"), hideOff(), true, false, CommandPermissionView.allowingAll());

        assertThat(sentAfterFilter(listener, "home", "op", "spawn")).containsExactly("home", "spawn");
    }

    @Test
    void aWhitelistKeepsOnlyItsListedCommands() {
        RuleSet whitelist = RuleSet.of(RuleMode.WHITELIST, List.of("home", "spawn"), Map.of(), BYPASS);
        CommandVisibilityListener listener =
                listener(whitelist, hideOff(), true, false, CommandPermissionView.allowingAll());

        assertThat(sentAfterFilter(listener, "home", "op", "spawn", "fly")).containsExactly("home", "spawn");
    }

    @Test
    void theVanillaPermissionOfACommandIsHonoured() {
        // The rule set allows both, but the player lacks the vanilla permission "home" carries, so it is scrubbed too.
        CommandPermissionView perms = (p, label) -> !label.equals("home");
        CommandVisibilityListener listener = listener(blacklist("zzz"), hideOff(), true, false, perms);

        assertThat(sentAfterFilter(listener, "home", "op")).containsExactly("op");
    }

    @Test
    void pluginHideRemovesHiddenCommandsIncludingNamespacedVariants() {
        CommandVisibilityListener listener =
                listener(blacklist(), hideOn(), true, false, CommandPermissionView.allowingAll());

        // blacklist is inert, so only the plugin-hide fires; the bukkit:/minecraft: variants are folded to their root.
        assertThat(sentAfterFilter(listener, "home", "plugins", "bukkit:plugins", "minecraft:help", "pl"))
                .containsExactly("home");
    }

    @Test
    void aBypassHolderKeepsEveryCommand() {
        when(player.hasPermission(BYPASS)).thenReturn(true);
        CommandVisibilityListener listener =
                listener(blacklist("op"), hideOn(), true, false, CommandPermissionView.allowingAll());

        assertThat(sentAfterFilter(listener, "home", "op", "plugins")).containsExactly("home", "op", "plugins");
    }

    @Test
    void aViewPermissionHolderKeepsThePluginCommands() {
        when(player.hasPermission(VIEW)).thenReturn(true);
        CommandVisibilityListener listener =
                listener(blacklist(), hideOn(), true, false, CommandPermissionView.allowingAll());

        assertThat(sentAfterFilter(listener, "home", "plugins", "pl")).containsExactly("home", "plugins", "pl");
    }

    @Test
    void bothGatesOffIsANoOp() {
        // Tab filter switched off and plugin-hide off: even a blacklisted command stays in the list.
        CommandVisibilityListener listener =
                listener(blacklist("op"), hideOff(), false, false, CommandPermissionView.allowingAll());

        assertThat(sentAfterFilter(listener, "home", "op")).containsExactly("home", "op");
    }

    @Test
    void scrubHelpCancelsAPluginCommandForANormalPlayerAndDeliversTheDeny() {
        CommandVisibilityListener listener =
                listener(blacklist(), hideOn(), true, true, CommandPermissionView.allowingAll());

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/plugins", null);
        listener.onCommandPreprocess(event);

        assertThat(event.isCancelled()).isTrue();
        verify(messages)
                .resolve(any(PlayerRef.class), eq(CommandControlMessageKey.COMMANDCONTROL_PLUGIN_HIDDEN), any());
        verify(sink).deliver(any(PlayerRef.class), eq("hidden"));
    }

    @Test
    void scrubHelpLetsAPermittedPlayerThrough() {
        when(player.hasPermission(VIEW)).thenReturn(true);
        CommandVisibilityListener listener =
                listener(blacklist(), hideOn(), true, true, CommandPermissionView.allowingAll());

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/plugins", null);
        listener.onCommandPreprocess(event);

        assertThat(event.isCancelled()).isFalse();
        verify(sink, never()).deliver(any(), anyString());
    }

    @Test
    void scrubHelpIsANoOpWhenDenyListIsOff() {
        // Plugin-hide is active (it still scrubs the sent list) but deny-list is off, so execution is not blocked.
        CommandVisibilityListener listener =
                listener(blacklist(), hideOn(), true, false, CommandPermissionView.allowingAll());

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/plugins", null);
        listener.onCommandPreprocess(event);

        assertThat(event.isCancelled()).isFalse();
        verify(sink, never()).deliver(any(), anyString());
    }

    @Test
    void asyncCompletionClearsTheArgumentsOfAFilteredCommand() {
        CommandVisibilityListener listener =
                listener(blacklist("op"), hideOff(), true, false, CommandPermissionView.allowingAll());

        AsyncTabCompleteEvent event = new AsyncTabCompleteEvent(player, "/op ", true, null);
        event.setCompletions(new ArrayList<>(List.of("Steve", "Alex")));
        listener.onTabComplete(event);

        assertThat(event.getCompletions()).isEmpty();
    }

    @Test
    void asyncCompletionKeepsTheArgumentsOfAnAllowedCommand() {
        CommandVisibilityListener listener =
                listener(blacklist("op"), hideOff(), true, false, CommandPermissionView.allowingAll());

        AsyncTabCompleteEvent event = new AsyncTabCompleteEvent(player, "/home ", true, null);
        event.setCompletions(new ArrayList<>(List.of("1", "2")));
        listener.onTabComplete(event);

        assertThat(event.getCompletions()).containsExactly("1", "2");
    }
}
