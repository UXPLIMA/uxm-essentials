package com.uxplima.uxmessentials.bootstrap.command;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.LocaleResolver;
import com.uxplima.uxmessentials.shared.application.message.LocaleScope;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleCatalog;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /lang}, a player's own language (docs/13-i18n §7). Cross-cutting rather than owned by a feature
 * context, so it lives in the bootstrap command surface alongside {@code /uxmess}. Permission node
 * {@code uxmessentials.lang.use} (default true), and {@code uxmessentials.lang.others} (op) for the two
 * branches that act on someone else.
 *
 * <pre>
 *   /lang                           show the current resolved locale, the override state, and the available locales
 *   /lang &lt;code&gt;                    set the override (must be a loaded locale), e.g. /lang tr
 *   /lang reset                     clear the override so the server decides again
 *   /lang set &lt;player&gt; &lt;code&gt;      set another player's, online or away
 *   /lang reset &lt;player&gt;            clear another player's, online or away
 * </pre>
 *
 * <p>The choice is kept on the player's own data, where every plugin of ours reads it, so a {@code /lang} here
 * is the language of all of them. A server with one audience forces a language in {@code config.conf}, and
 * then the player's own branches say so rather than change something nobody reads. An operator's choice is
 * still kept while a language is forced, and the operator is told it waits.
 *
 * <p>Every reply resolves through a {@link SharedMessageKey}. {@code /lang tr} validates against
 * {@link LocaleCatalog#availableLocales()}; a code with no catalogue yields {@link SharedMessageKey#LANG_UNKNOWN}.
 */
@NullMarked
public final class LangCommand implements CommandRegistration {

    private static final String LITERAL = "lang";
    private static final String PERMISSION = "uxmessentials.lang.use";
    private static final String OTHERS = "uxmessentials.lang.others";
    private static final String RESET_LITERAL = "reset";
    private static final String SET_LITERAL = "set";

    private final LocaleStore overrides;
    private final LocaleCatalog catalog;
    private final Messages messages;
    private final MessageSink sink;
    private final LocaleResolver resolver;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final CommandFeedback feedback;

    public LangCommand(
            LocaleStore overrides,
            LocaleCatalog catalog,
            Messages messages,
            MessageSink sink,
            LocaleResolver resolver,
            PlayerLookup players,
            Scheduler scheduler) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = new CommandFeedback(messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(LITERAL)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runStatus)
                .then(Commands.literal(RESET_LITERAL)
                        .executes(this::runReset)
                        .then(Commands.argument("player", Args.token())
                                .requires(src -> src.getSender().hasPermission(OTHERS))
                                .suggests(CommandSuggestions.onlinePlayers())
                                .executes(this::runResetOther)))
                .then(Commands.literal(SET_LITERAL)
                        .requires(src -> src.getSender().hasPermission(OTHERS))
                        .then(Commands.argument("player", Args.token())
                                .suggests(CommandSuggestions.onlinePlayers())
                                .then(Commands.argument("code", Args.token()).executes(this::runSetOther))))
                .then(Commands.argument("code", Args.token()).executes(this::runSet))
                .build();
    }

    @Override
    public String description() {
        return "Set or clear your personal language override.";
    }

    private int runStatus(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        sink.deliver(
                viewer,
                messages.resolve(
                        viewer,
                        SharedMessageKey.LANG_STATUS,
                        Map.of("locale", currentName(player), "available", availableCodes())));
        return Command.SINGLE_SUCCESS;
    }

    private int runReset(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(ctx);
        if (player == null || refusedBecauseForced(player)) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        overrides.clearOverride(viewer);
        sink.deliver(viewer, messages.resolve(viewer, SharedMessageKey.LANG_RESET, Map.of()));
        player.updateCommands();
        return Command.SINGLE_SUCCESS;
    }

    private int runSet(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(ctx);
        if (player == null || refusedBecauseForced(player)) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        String code = ctx.getArgument("code", String.class);
        Locale requested = Locale.forLanguageTag(code);
        if (!isAvailable(requested)) {
            sink.deliver(
                    viewer,
                    messages.resolve(
                            viewer,
                            SharedMessageKey.LANG_UNKNOWN,
                            Map.of("code", code, "available", availableCodes())));
            return 0;
        }
        overrides.setOverride(viewer, requested);
        sink.deliver(
                viewer,
                messages.resolve(
                        viewer, SharedMessageKey.LANG_SET, Map.of("locale", requested.getDisplayLanguage(requested))));
        player.updateCommands();
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /lang set <player> <code>}: an operator sets another player's language, here and in every plugin. */
    private int runSetOther(CommandContext<CommandSourceStack> ctx) {
        CommandSender operator = Sender.audience(ctx.getSource());
        String code = ctx.getArgument("code", String.class);
        Locale requested = Locale.forLanguageTag(code);
        if (!isAvailable(requested)) {
            feedback.send(operator, SharedMessageKey.LANG_UNKNOWN, Map.of("code", code, "available", availableCodes()));
            return 0;
        }
        Optional<PlayerRef> target = named(operator, ctx.getArgument("player", String.class));
        if (target.isEmpty()) {
            return 0;
        }
        PlayerRef who = target.get();
        overrides.setOverride(who, requested);
        Map<String, String> said = Map.of("player", who.name(), "locale", requested.getDisplayLanguage(requested));
        if (players.isOnline(who.uuid())) {
            tell(who, SharedMessageKey.LANG_SET, Map.of("locale", requested.getDisplayLanguage(requested)));
            feedback.send(operator, SharedMessageKey.LANG_SET_PLAYER, said);
        } else {
            feedback.send(operator, SharedMessageKey.LANG_SET_PLAYER_AWAY, said);
        }
        sayWhenForced(operator);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /lang reset <player>}: an operator clears another player's language, so the server decides again. */
    private int runResetOther(CommandContext<CommandSourceStack> ctx) {
        CommandSender operator = Sender.audience(ctx.getSource());
        Optional<PlayerRef> target = named(operator, ctx.getArgument("player", String.class));
        if (target.isEmpty()) {
            return 0;
        }
        PlayerRef who = target.get();
        overrides.clearOverride(who);
        if (players.isOnline(who.uuid())) {
            tell(who, SharedMessageKey.LANG_RESET, Map.of());
            feedback.send(operator, SharedMessageKey.LANG_RESET_PLAYER, Map.of("player", who.name()));
        } else {
            feedback.send(operator, SharedMessageKey.LANG_RESET_PLAYER_AWAY, Map.of("player", who.name()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** The player an operator named, online or one the server has seen, or a reply that there is nobody. */
    private Optional<PlayerRef> named(CommandSender operator, String typed) {
        Optional<PlayerRef> found = players.findByName(typed);
        if (found.isEmpty()) {
            feedback.send(operator, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", typed));
        }
        return found;
    }

    /**
     * Tell the player whose language just changed, in that language, and send them their commands again.
     *
     * <p>The line is resolved here, on the operator's thread, whose request scope carries the operator's
     * language. It is resolved inside the target's own instead, so a reset reads in the language they now read.
     */
    private void tell(PlayerRef who, SharedMessageKey key, Map<String, String> placeholders) {
        Locale client = resolver.clients().of(who.uuid()).orElse(Locale.ROOT);
        LocaleScope.runWith(
                resolver.resolveAt(who, client), () -> sink.deliver(who, messages.resolve(who, key, placeholders)));
        scheduler.onEntity(who, () -> {
            Player online = Bukkit.getPlayer(who.uuid());
            if (online != null) {
                online.updateCommands();
            }
        });
    }

    /** A player's own branch, refused while the server forces a language, which nobody chooses past. */
    private boolean refusedBecauseForced(Player player) {
        Optional<Locale> forced = resolver.settings().forcedLocale();
        if (forced.isEmpty()) {
            return false;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        sink.deliver(
                viewer,
                messages.resolve(
                        viewer,
                        SharedMessageKey.LANG_FORCED,
                        Map.of("locale", forced.get().getDisplayLanguage(forced.get()))));
        return true;
    }

    private void sayWhenForced(CommandSender operator) {
        resolver.settings()
                .forcedLocale()
                .ifPresent(forced -> feedback.send(
                        operator,
                        SharedMessageKey.LANG_FORCED_ANYWAY,
                        Map.of("locale", forced.getDisplayLanguage(forced))));
    }

    private boolean isAvailable(Locale requested) {
        String language = requested.getLanguage();
        return !language.isEmpty()
                && catalog.availableLocales().stream()
                        .anyMatch(available -> available.getLanguage().equals(language));
    }

    private String currentName(Player player) {
        Locale resolved = resolver.resolveAt(BukkitRefs.toRef(player), player.locale());
        return resolved.getDisplayLanguage(resolved);
    }

    private String availableCodes() {
        return catalog.availableLocales().stream()
                .map(Locale::getLanguage)
                .filter(language -> !language.isEmpty())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /** The invoking player, or {@code null} (after the players-only reply) for a console source. */
    private @org.jspecify.annotations.Nullable Player playerOf(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        if (sender instanceof Player player) {
            return player;
        }
        // A console has no client locale of its own; reply directly rather than through the entity-scheduler
        // sink (which no-ops for a non-player), mirroring the feature commands' players-only path.
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of());
        return null;
    }
}
