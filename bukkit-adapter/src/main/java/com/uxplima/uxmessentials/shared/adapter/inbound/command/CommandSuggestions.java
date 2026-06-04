package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Reusable Brigadier suggestion providers for the inbound command surface. Every provider here runs on the
 * tick thread (Brigadier invokes suggestion callbacks synchronously while the client types), so each one is
 * deliberately allocation-light and never touches I/O — it reads only live in-memory state and filters by the
 * partial token the player has typed so far.
 *
 * <p>The two building blocks cover the two shapes Wave 2 needs: {@link #onlinePlayers()} for a player target
 * that should complete against the online roster, and {@link #fromStrings(Supplier)} for any dynamic name list
 * (currency ids, warp names, kit ids) sourced from an in-memory, side-effect-free supplier.
 */
@NullMarked
public final class CommandSuggestions {

    private CommandSuggestions() {}

    /**
     * Suggests the names of every online player, prefix-filtered (case-insensitively) by the partial token the
     * sender has typed. Used for player-target arguments that keep a string type because they must also accept
     * offline names — the suggestion only surfaces online matches, but the argument still parses anything typed.
     */
    public static SuggestionProvider<CommandSourceStack> onlinePlayers() {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                String name = online.getName();
                if (matches(name, prefix)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    /**
     * Suggests every value the supplier returns, prefix-filtered (case-insensitively) by the partial token. The
     * supplier must be side-effect-free and non-blocking — it is invoked on the tick thread per keystroke, so it
     * should read only in-memory state (a registry, an in-memory cache peek). A {@code null} or empty result
     * simply yields no suggestions.
     */
    public static SuggestionProvider<CommandSourceStack> fromStrings(Supplier<? extends Collection<String>> values) {
        Objects.requireNonNull(values, "values");
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            Collection<String> current = values.get();
            if (current != null) {
                for (String value : current) {
                    if (value != null && matches(value, prefix)) {
                        builder.suggest(value);
                    }
                }
            }
            return builder.buildFuture();
        };
    }

    /**
     * Suggests a per-viewer list of names, prefix-filtered. The lookup receives the invoking player as a
     * {@link PlayerRef} so a permission-filtered, viewer-scoped source (the warps/kits/pwarps a player may
     * use, the homes they own) can be returned. A non-player source (console) yields no suggestions. The
     * lookup runs on the tick thread per keystroke, so it must read only non-blocking in-memory state.
     */
    public static SuggestionProvider<CommandSourceStack> forPlayer(
            Function<PlayerRef, ? extends Collection<String>> lookup) {
        Objects.requireNonNull(lookup, "lookup");
        return (ctx, builder) -> {
            CommandSender sender = ctx.getSource().getSender();
            if (!(sender instanceof Player player)) {
                return builder.buildFuture();
            }
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            Collection<String> names = lookup.apply(BukkitRefs.toRef(player));
            if (names != null) {
                for (String name : names) {
                    if (name != null && matches(name, prefix)) {
                        builder.suggest(name);
                    }
                }
            }
            return builder.buildFuture();
        };
    }

    private static boolean matches(String candidate, String lowerPrefix) {
        return lowerPrefix.isEmpty() || candidate.toLowerCase(Locale.ROOT).startsWith(lowerPrefix);
    }

    /**
     * A single-word string argument pre-wired to complete against the online roster. This is the drop-in for a
     * player-target argument that must still accept offline names (it stays a string), giving online-name
     * completion without changing how the value is parsed or resolved downstream.
     */
    public static RequiredArgumentBuilder<CommandSourceStack, String> playerArgument(String name) {
        Objects.requireNonNull(name, "name");
        return Commands.argument(name, StringArgumentType.word()).suggests(onlinePlayers());
    }
}
