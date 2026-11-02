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

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.uxplima.uxmessentials.commandcontrol.adapter.CommandControlState;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.BukkitPlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.CommandPermissionView;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.CommandTreePruner;
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
 *   <li>{@link AsyncPlayerSendCommandsEvent}, takes the fixed words a player may not use out of the command tree
 *       their client completes from on its own, so {@code /gamemode} offers {@code creative} but not
 *       {@code spectator} when the lists name those arguments.
 *   <li>{@link AsyncTabCompleteEvent} and {@link AsyncPlayerSendSuggestionsEvent}. Drop every suggestion a player may
 *       not use: all of them for a command they cannot see, and one by one where the lists name arguments, so a
 *       command a player cannot run never leaks its arguments through completion.
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
                .removeIf(label -> shouldScrubFromList(
                        tabCompletion, rulesInForce.blockNamespaceBypass(), rules, hidePolicy, label, player, facts));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(AsyncTabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player) || !event.isCommand()) {
            return;
        }
        String buffer = event.getBuffer();
        if (buffer.indexOf(' ') < 0) {
            // Completing the command name itself: the sent command list already governs which roots the client knows.
            return;
        }
        Filter filter = filterFor(player);
        if (filter == null) {
            return;
        }
        if (filter.hidesCommand(buffer)) {
            event.setCompletions(List.of());
            return;
        }
        String typed = buffer.substring(0, buffer.lastIndexOf(' ') + 1);
        List<AsyncTabCompleteEvent.Completion> offered = event.completions();
        List<AsyncTabCompleteEvent.Completion> kept = offered.stream()
                .filter(completion -> filter.shows(typed + completion.suggestion()))
                .toList();
        if (kept.size() != offered.size()) {
            event.completions(kept);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSendSuggestions(AsyncPlayerSendSuggestionsEvent event) {
        String buffer = event.getBuffer();
        if (buffer.indexOf(' ') < 0) {
            return;
        }
        Filter filter = filterFor(event.getPlayer());
        if (filter == null) {
            return;
        }
        if (filter.hidesCommand(buffer)) {
            event.setCancelled(true);
            return;
        }
        Suggestions offered = event.getSuggestions();
        List<Suggestion> kept = offered.getList().stream()
                .filter(suggestion -> filter.shows(
                        buffer.substring(0, Math.min(suggestion.getRange().getStart(), buffer.length()))
                                + suggestion.getText()))
                .toList();
        if (kept.size() != offered.getList().size()) {
            event.setSuggestions(new Suggestions(offered.getRange(), kept));
        }
    }

    @EventHandler
    public void onSendCommandTree(AsyncPlayerSendCommandsEvent<?> event) {
        if (!event.isAsynchronous() && event.hasFiredAsync()) {
            // Already trimmed when the tree was built off the main thread.
            return;
        }
        Filter filter = filterFor(event.getPlayer());
        if (filter == null || !filter.tabActive()) {
            return;
        }
        CommandTreePruner.prune(event.getCommandNode(), filter::namesArguments, filter::shows);
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
            boolean foldNamespace,
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
        return !rules.visible(label, facts, foldNamespace) || !permissions.canSee(player, label);
    }

    /**
     * The suggestion filter for {@code player} in the world they stand in, or {@code null} when nothing would be
     * filtered: the tab filter and the plugin-hide are both off there, or the player holds the bypass.
     */
    private @Nullable Filter filterFor(Player player) {
        CommandControlState.Snapshot rulesInForce = state.current();
        boolean tabCompletion = rulesInForce.config().tabCompletionEnabled();
        RuleSet rules = rulesInForce.worldRules().forWorld(worldName(player));
        HidePolicy hidePolicy = rulesInForce.worldHidePolicies().forWorld(worldName(player));
        boolean tabActive = tabActive(tabCompletion, rules);
        if (!tabActive && !hidePolicy.isActive()) {
            return null;
        }
        if (player.hasPermission(bypassPermission)) {
            return null;
        }
        return new Filter(
                rules,
                hidePolicy,
                tabActive,
                rulesInForce.blockNamespaceBypass(),
                new BukkitPlayerFacts(player, groups));
    }

    /** What one player may be shown, read from the rules in force when the filter was made. */
    private record Filter(
            RuleSet rules, HidePolicy hidePolicy, boolean tabActive, boolean foldNamespace, PlayerFacts facts) {

        /** Whether the command typed at the front of {@code buffer} is hidden, so none of its arguments are offered. */
        boolean hidesCommand(String buffer) {
            String root = commandRoot(buffer);
            return hidePolicy.shouldHide(root, facts) || (tabActive && !rules.visible(root, facts, foldNamespace));
        }

        /** Whether {@code command}, a command with the arguments typed so far, may be offered. */
        boolean shows(String command) {
            return !tabActive || rules.visible(command, facts, foldNamespace);
        }

        /** Whether the lists name any argument of {@code root}, so its arguments are worth judging one by one. */
        boolean namesArguments(String root) {
            return rules.namesArgumentsOf(root, facts, foldNamespace);
        }
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
