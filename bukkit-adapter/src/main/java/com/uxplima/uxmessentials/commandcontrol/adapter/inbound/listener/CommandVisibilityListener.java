package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.uxplima.uxmessentials.commandcontrol.adapter.CommandControlState;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.BukkitPlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.CommandPermissionView;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.application.CommandControlMessageKey;
import com.uxplima.uxmessentials.commandcontrol.application.LanguageWords;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The command-visibility half of command-control: it keeps disallowed and hidden commands out of what a client sees.
 *
 * <ul>
 *   <li>{@link PlayerCommandSendEvent}, scrubs the server-side command list sent to a client, removing every command
 *       the world's {@link RuleSet} denies (respecting the vanilla permission the command already carries) and every
 *       plugin-listing command the world's {@link HidePolicy} hides, so a disallowed command neither autocompletes nor
 *       appears in the client command graph. The list is client-agnostic - Bedrock / Geyser players receive it too.
 *       It also drops the words {@link LanguageWords} gives to languages the player does not read.
 *   <li>{@link AsyncTabCompleteEvent}. Drops the argument suggestions of a command whose root is filtered, so the
 *       arguments of a command a player cannot run never leak through completion.
 *   <li>{@link PlayerCommandPreprocessEvent}. The scrub-help block: when {@code deny-list-commands} is on, a
 *       {@code /plugins} or {@code /help} typed by a player who may not see the plugin list is cancelled and answered
 *       with the deny line, so plugin names are not leaked through the built-in help output.
 *   <li>{@link PlayerChangedWorldEvent} - when a per-world override is configured, re-sends the command graph on a
 *       world change so the sent-list scrub is recomputed for the new world's rule set / hide policy.
 * </ul>
 *
 * <p>Every path resolves the rule set and hide policy of the player's <em>current</em> world, so a per-world override
 * governs the player while they are in that world and the base governs them elsewhere. A {@code .bypass} holder is
 * short-circuited before any work - the bypass sees everything. The listener does nothing when both the tab filter
 * (rule set inert or switched off) and the plugin-hide are inactive for the player's world.
 */
@NullMarked
public final class CommandVisibilityListener implements Listener {

    private final CommandControlState state;
    private final PlayerGroupSource groups;
    private final CommandPermissionView permissions;
    private final String bypassPermission;
    private final Messages messages;
    private final MessageSink sink;
    private final Function<UUID, Locale> languages;

    public CommandVisibilityListener(
            CommandControlState state,
            PlayerGroupSource groups,
            CommandPermissionView permissions,
            String bypassPermission,
            Messages messages,
            MessageSink sink,
            Function<UUID, Locale> languages) {
        this.state = Objects.requireNonNull(state, "state");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        if (bypassPermission == null || bypassPermission.isBlank()) {
            throw new IllegalArgumentException("bypassPermission must be non-blank");
        }
        this.bypassPermission = bypassPermission;
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        CommandControlState.Snapshot rulesInForce = state.current();
        LanguageWords languageWords = rulesInForce.languageWords();
        if (!languageWords.isEmpty()) {
            // Cosmetic and for everybody, a bypass holder included: a word for another language still runs when typed.
            Locale reads = languages.apply(player.getUniqueId());
            event.getCommands().removeIf(label -> languageWords.hiddenFor(label, reads));
        }
        boolean tabCompletion = rulesInForce.config().tabCompletionEnabled();
        RuleSet rules = rulesInForce.worldRules().forWorld(worldName(player));
        HidePolicy hidePolicy = rulesInForce.worldHidePolicies().forWorld(worldName(player));
        if (!tabActive(tabCompletion, rules) && !hidePolicy.isActive()) {
            return;
        }
        if (player.hasPermission(bypassPermission)) {
            return;
        }
        PlayerFacts facts = new BukkitPlayerFacts(player, groups);
        event.getCommands()
                .removeIf(label -> shouldScrubFromList(tabCompletion, rules, hidePolicy, label, player, facts));
    }

    @EventHandler(ignoreCancelled = true)
    public void onTabComplete(AsyncTabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player) || !event.isCommand()) {
            return;
        }
        CommandControlState.Snapshot rulesInForce = state.current();
        boolean tabCompletion = rulesInForce.config().tabCompletionEnabled();
        RuleSet rules = rulesInForce.worldRules().forWorld(worldName(player));
        HidePolicy hidePolicy = rulesInForce.worldHidePolicies().forWorld(worldName(player));
        if (!tabActive(tabCompletion, rules) && !hidePolicy.isActive()) {
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
        if (isFilteredRoot(tabCompletion, rules, hidePolicy, commandRoot(buffer), facts)) {
            event.setCompletions(List.of());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        CommandControlState.Snapshot rulesInForce = state.current();
        if (!rulesInForce.config().denyListCommands()) {
            return;
        }
        Player player = event.getPlayer();
        HidePolicy hidePolicy = rulesInForce.worldHidePolicies().forWorld(worldName(player));
        if (!hidePolicy.isActive()) {
            return;
        }
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

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Only when the command visibility can actually differ by world; otherwise the sent list would not change and a
        // resend would be wasted work. The event fires on the player's region thread, so the resend is region-local.
        CommandControlState.Snapshot rulesInForce = state.current();
        if (rulesInForce.worldRules().hasWorldOverrides()
                || rulesInForce.worldHidePolicies().hasWorldOverrides()) {
            event.getPlayer().updateCommands();
        }
    }

    /** A label is scrubbed from the sent list when the hide covers it or the rule set / vanilla permission denies it. */
    private boolean shouldScrubFromList(
            boolean tabCompletion,
            RuleSet rules,
            HidePolicy hidePolicy,
            String label,
            Player player,
            PlayerFacts facts) {
        if (hidePolicy.shouldHide(label, facts)) {
            return true;
        }
        if (!tabActive(tabCompletion, rules)) {
            return false;
        }
        return rules.decide(label, facts) == RuleSet.Decision.DENY || !permissions.canSee(player, label);
    }

    /** The pure (thread-safe) filter test for the async completion path: hidden, or denied by an active rule set. */
    private static boolean isFilteredRoot(
            boolean tabCompletion, RuleSet rules, HidePolicy hidePolicy, String root, PlayerFacts facts) {
        return hidePolicy.shouldHide(root, facts)
                || (tabActive(tabCompletion, rules) && rules.decide(root, facts) == RuleSet.Decision.DENY);
    }

    /** True when the tab-completion filter can remove anything - switched on and the world's rule set is not inert. */
    private static boolean tabActive(boolean tabCompletion, RuleSet rules) {
        return tabCompletion && !rules.isInert();
    }

    /** The player's current world name, or {@code null} when it cannot be read (falls back to the base policy). */
    private static @Nullable String worldName(Player player) {
        World world = player.getWorld();
        return world == null ? null : world.getName();
    }

    /** The command label: leading slash stripped, arguments dropped, lowercased. */
    private static String commandRoot(String raw) {
        String body = raw.startsWith("/") ? raw.substring(1) : raw;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        return label.toLowerCase(Locale.ROOT);
    }
}
