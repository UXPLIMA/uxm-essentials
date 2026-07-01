package com.uxplima.uxmessentials.custommenus.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /menu} — the operator surface over the loaded custom menus. {@code /menu open <name>} opens a registered
 * spec for the clicking player (gated by {@code uxmessentials.menu.use}); {@code /menu open <name> <target>} opens
 * it for another player (gated by {@code uxmessentials.menu.open.others}, so an operator can push a menu to someone
 * and the console can too); {@code /menu list} prints the loaded menu names; {@code /menu last} reopens the last
 * custom menu the player had open (with its page and typed arguments); {@code /menu reload} re-runs the loader and
 * reports the loaded/skipped counts (gated by {@code uxmessentials.menu.admin}). The set of registered names is
 * supplied by the wiring rather than read off the engine, so the same list backs the {@code <name>} tab-completion,
 * the not-found guard, {@code /menu list}, and the still-registered check {@code /menu last} makes before reopening.
 */
@NullMarked
public final class MenuCommand implements CommandRegistration {

    private static final String USE = "uxmessentials.menu.use";
    private static final String ADMIN = "uxmessentials.menu.admin";
    private static final String OPEN_OTHERS = "uxmessentials.menu.open.others";

    private final Menus menus;
    private final Supplier<List<String>> menuNames;
    private final Supplier<CustomMenuLoader.LoadResult> reload;
    private final CommandFeedback feedback;

    public MenuCommand(
            Menus menus,
            Supplier<List<String>> menuNames,
            Supplier<CustomMenuLoader.LoadResult> reload,
            Messages messages) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.menuNames = Objects.requireNonNull(menuNames, "menuNames");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("menu")
                .requires(src -> src.getSender().hasPermission(USE))
                .then(Commands.literal("open")
                        .then(nameArgument()
                                .executes(this::open)
                                .then(targetArgument()
                                        .requires(src -> src.getSender().hasPermission(OPEN_OTHERS))
                                        .executes(this::openForOther))))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("last").executes(this::last))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .executes(this::reload))
                .build();
    }

    @Override
    public String description() {
        return "Open, list, and reload operator custom menus.";
    }

    /** The {@code <name>} argument, completed from the currently registered menu names. */
    private RequiredArgumentBuilder<CommandSourceStack, String> nameArgument() {
        return Commands.argument("name", StringArgumentType.word()).suggests(nameSuggestions());
    }

    private SuggestionProvider<CommandSourceStack> nameSuggestions() {
        return (ctx, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (String name : menuNames.get()) {
                if (name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        if (!menuNames.get().contains(name)) {
            feedback.send(player, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", name));
            return 0;
        }
        menus.open(BukkitRefs.toRef(player), name, null);
        return Command.SINGLE_SUCCESS;
    }

    /** The {@code <target>} player argument, present only for a sender holding {@code uxmessentials.menu.open.others}. */
    private RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> targetArgument() {
        return Commands.argument("target", ArgumentTypes.player());
    }

    /**
     * Open a loaded menu for another player. The menu name is checked first, so an unknown name draws the same
     * not-found line the self-open does before any target work. The target is resolved from the single-player
     * selector; a selector that matches nobody draws the shared unknown-player line rather than opening an empty
     * window. The sender may be a player or the console — this never opens for the sender, only for the resolved
     * target, so the console can legitimately push a menu to a real player. The menu itself opens on the target's
     * own region thread through the {@link Menus} facade, so cross-region opens stay Folia-safe.
     */
    private int openForOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        if (!menuNames.get().contains(name)) {
            feedback.send(sender, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", name));
            return 0;
        }
        Player target = resolveTarget(ctx);
        if (target == null) {
            feedback.send(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER);
            return 0;
        }
        menus.open(BukkitRefs.toRef(target), name, null);
        feedback.send(sender, CustomMenusMessageKey.MENU_OPENED_FOR, Map.of("player", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    /** Resolve the single target the {@code <target>} selector names, or null when it matched none (or more than one). */
    private static @Nullable Player resolveTarget(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        List<Player> resolved = resolver.resolve(ctx.getSource());
        return resolved.size() == 1 ? resolved.get(0) : null;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<String> names = menuNames.get();
        if (names.isEmpty()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_LIST_EMPTY);
            return Command.SINGLE_SUCCESS;
        }
        feedback.send(sender, CustomMenusMessageKey.MENU_LIST_HEADER, Map.of("count", String.valueOf(names.size())));
        for (String name : names) {
            feedback.send(sender, CustomMenusMessageKey.MENU_LIST_ENTRY, Map.of("name", name));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Reopen the last custom menu the player had open, with the page and typed arguments it was shown with. Only a
     * player has a remembered open, so the console is turned away with the shared players-only line. The reopen is
     * delegated to {@link Menus#reopenLast}, which replays the recorded open without re-recording it (so repeated
     * {@code /menu last} never grows the back history) and returns {@code false} when there is nothing to reopen: a
     * player who never opened a menu, or whose remembered menu is no longer a registered spec (a since-deleted or
     * renamed one). That yields the no-last line rather than the loud unknown-spec failure a blind reopen would raise.
     * The reopen lands on the player's own region thread through the facade, so it stays Folia-safe.
     */
    private int last(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        if (!menus.reopenLast(BukkitRefs.toRef(player))) {
            feedback.send(player, CustomMenusMessageKey.MENU_NO_LAST);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CustomMenuLoader.LoadResult result = reload.get();
        feedback.send(
                ctx.getSource().getSender(),
                CustomMenusMessageKey.MENU_RELOADED,
                Map.of(
                        "loaded",
                        String.valueOf(result.loaded()),
                        "skipped",
                        String.valueOf(result.skipped().size())));
        return Command.SINGLE_SUCCESS;
    }
}
