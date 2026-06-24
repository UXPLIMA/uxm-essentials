package com.uxplima.uxmessentials.custommenus.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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

/**
 * {@code /menu} — the operator surface over the loaded custom menus. {@code /menu open <name>} opens a registered
 * spec for the clicking player (gated by {@code uxmessentials.menu.use}); {@code /menu list} prints the loaded menu
 * names; {@code /menu reload} re-runs the loader and reports the loaded/skipped counts (gated by
 * {@code uxmessentials.menu.admin}). The set of registered names is supplied by the wiring rather than read off the
 * engine, so the same list backs the {@code <name>} tab-completion, the not-found guard, and {@code /menu list}.
 */
@NullMarked
public final class MenuCommand implements CommandRegistration {

    private static final String USE = "uxmessentials.menu.use";
    private static final String ADMIN = "uxmessentials.menu.admin";

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
                .then(Commands.literal("open").then(nameArgument().executes(this::open)))
                .then(Commands.literal("list").executes(this::list))
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
