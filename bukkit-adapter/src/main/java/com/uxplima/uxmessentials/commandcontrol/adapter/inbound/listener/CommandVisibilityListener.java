package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.CommandPermissionView;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.application.CommandControlMessageKey;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The command-visibility half of command-control: it keeps disallowed and hidden commands out of what a client sees.
 *
 * <ul>
 *   <li>{@link PlayerCommandSendEvent} — scrubs the server-side command list sent to a client, removing every command
 *       the {@link RuleSet} denies (respecting the vanilla permission the command already carries) and every
 *       plugin-listing command the {@link HidePolicy} hides, so a disallowed command neither autocompletes nor appears
 *       in the client command graph. The list is client-agnostic — Bedrock / Geyser players receive it too.
 *   <li>{@link AsyncTabCompleteEvent} — drops the argument suggestions of a command whose root is filtered, so the
 *       arguments of a command a player cannot run never leak through completion.
 *   <li>{@link PlayerCommandPreprocessEvent} — the scrub-help block: when {@code deny-list-commands} is on, a
 *       {@code /plugins} or {@code /help} typed by a player who may not see the plugin list is cancelled and answered
 *       with the deny line, so plugin names are not leaked through the built-in help output.
 * </ul>
 *
 * <p>A {@code .bypass} holder is short-circuited before any work — the bypass sees everything. The listener does
 * nothing when both the tab filter (rule set inert or switched off) and the plugin-hide are inactive.
 */
@NullMarked
public final class CommandVisibilityListener implements Listener {

    private final RuleSet rules;
    private final HidePolicy hidePolicy;
    private final PlayerGroupSource groups;
    private final CommandPermissionView permissions;
    private final boolean tabCompletionEnabled;
    private final boolean scrubHelp;
    private final String bypassPermission;
    private final Messages messages;
    private final MessageSink sink;

    public CommandVisibilityListener(
            RuleSet rules,
            HidePolicy hidePolicy,
            PlayerGroupSource groups,
            CommandPermissionView permissions,
            boolean tabCompletionEnabled,
            boolean scrubHelp,
            String bypassPermission,
            Messages messages,
            MessageSink sink) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.hidePolicy = Objects.requireNonNull(hidePolicy, "hidePolicy");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.tabCompletionEnabled = tabCompletionEnabled;
        this.scrubHelp = scrubHelp;
        if (bypassPermission == null || bypassPermission.isBlank()) {
            throw new IllegalArgumentException("bypassPermission must be non-blank");
        }
        this.bypassPermission = bypassPermission;
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!tabActive() && !hidePolicy.isActive()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(bypassPermission)) {
            return;
        }
        PlayerFacts facts = new BukkitPlayerFacts(player, groups);
        event.getCommands().removeIf(label -> shouldScrubFromList(label, player, facts));
    }

    @EventHandler(ignoreCancelled = true)
    public void onTabComplete(AsyncTabCompleteEvent event) {
        if (!tabActive() && !hidePolicy.isActive()) {
            return;
        }
        if (!(event.getSender() instanceof Player player) || !event.isCommand()) {
            return;
        }
        if (player.hasPermission(bypassPermission)) {
            return;
        }
        String buffer = event.getBuffer();
        if (buffer.indexOf(' ') < 0) {
            // Completing the command name itself: the sent command list already governs which roots the client knows.
            return;
        }
        PlayerFacts facts = new BukkitPlayerFacts(player, groups);
        if (isFilteredRoot(commandRoot(buffer), facts)) {
            event.setCompletions(List.of());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!scrubHelp || !hidePolicy.isActive()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(bypassPermission)) {
            return;
        }
        PlayerFacts facts = new BukkitPlayerFacts(player, groups);
        if (!hidePolicy.shouldHide(commandRoot(event.getMessage()), facts)) {
            return;
        }
        event.setCancelled(true);
        PlayerRef who = BukkitRefs.toRef(player);
        sink.deliver(who, messages.resolve(who, CommandControlMessageKey.COMMANDCONTROL_PLUGIN_HIDDEN, Map.of()));
    }

    /** A label is scrubbed from the sent list when the hide covers it or the rule set / vanilla permission denies it. */
    private boolean shouldScrubFromList(String label, Player player, PlayerFacts facts) {
        if (hidePolicy.shouldHide(label, facts)) {
            return true;
        }
        if (!tabActive()) {
            return false;
        }
        return rules.decide(label, facts) == RuleSet.Decision.DENY || !permissions.canSee(player, label);
    }

    /** The pure (thread-safe) filter test for the async completion path: hidden, or denied by an active rule set. */
    private boolean isFilteredRoot(String root, PlayerFacts facts) {
        return hidePolicy.shouldHide(root, facts)
                || (tabActive() && rules.decide(root, facts) == RuleSet.Decision.DENY);
    }

    /** True when the tab-completion filter can remove anything — switched on and the rule set is not inert. */
    private boolean tabActive() {
        return tabCompletionEnabled && !rules.isInert();
    }

    /** The command label: leading slash stripped, arguments dropped, lowercased. */
    private static String commandRoot(String raw) {
        String body = raw.startsWith("/") ? raw.substring(1) : raw;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        return label.toLowerCase(Locale.ROOT);
    }
}
