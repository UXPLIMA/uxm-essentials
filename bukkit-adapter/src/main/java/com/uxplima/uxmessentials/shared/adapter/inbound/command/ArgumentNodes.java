package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec.ArgType;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the typed Brigadier argument chain a config-declared command carries, and reads the parsed values back
 * out. One implementation serves both callers: a menu's {@code command {}} block and a custom command definition
 * declare their arguments in the same shape, so they get the same parsing, the same autocomplete and the same
 * error messages.
 *
 * <p>Nodes nest innermost-last: the deepest node always carries the executor, and every node whose successor is
 * optional carries it too, which is what makes a trailing run of optional arguments droppable. Brigadier can only
 * read the remaining input on a terminal node, so a greedy flag is honoured only on the last argument and only for
 * a string type.
 */
@NullMarked
public final class ArgumentNodes {

    private ArgumentNodes() {}

    /**
     * Nest the typed nodes for {@code args} in declared order and hand back the outermost one, ready to be attached
     * under a command literal. Requires at least one argument: a command with none attaches its executor to the
     * literal itself.
     */
    public static ArgumentBuilder<CommandSourceStack, ?> chain(
            List<ArgumentSpec> args, Command<CommandSourceStack> executor) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(executor, "executor");
        if (args.isEmpty()) {
            throw new IllegalArgumentException("an argument chain needs at least one argument");
        }
        int last = args.size() - 1;
        RequiredArgumentBuilder<CommandSourceStack, ?> node = node(args.get(last), true);
        node.executes(executor);
        for (int i = last - 1; i >= 0; i--) {
            RequiredArgumentBuilder<CommandSourceStack, ?> outer = node(args.get(i), false);
            // An argument is executable in its own right exactly when everything after it may be omitted, which is
            // the same thing as the next argument being optional: optional arguments form a trailing run.
            if (args.get(i + 1).optional()) {
                outer.executes(executor);
            }
            node = outer.then(node);
        }
        return node;
    }

    /**
     * Read every declared argument off {@code ctx}, keyed by name in declared order, as its rendered text. An
     * optional argument the sender left out reads as the empty string rather than throwing.
     */
    public static Map<String, String> read(CommandContext<CommandSourceStack> ctx, List<ArgumentSpec> args)
            throws CommandSyntaxException {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(args, "args");
        Map<String, String> values = new LinkedHashMap<>();
        for (ArgumentSpec arg : args) {
            values.put(arg.name(), present(ctx, arg.name()) ? stringify(ctx, arg) : "");
        }
        return values;
    }

    /** Whether Brigadier actually parsed a value under {@code name} on the path that reached this executor. */
    private static boolean present(CommandContext<CommandSourceStack> ctx, String name) {
        for (ParsedCommandNode<CommandSourceStack> parsed : ctx.getNodes()) {
            if (name.equals(parsed.getNode().getName())) {
                return true;
            }
        }
        return false;
    }

    /** Build one typed Brigadier argument node with its autocomplete for {@code arg}; {@code last} gates greedy capture. */
    private static RequiredArgumentBuilder<CommandSourceStack, ?> node(ArgumentSpec arg, boolean last) {
        RequiredArgumentBuilder<CommandSourceStack, ?> node = Commands.argument(arg.name(), argumentType(arg, last));
        suggestionsFor(arg.type()).ifPresent(node::suggests);
        return node;
    }

    /**
     * The Brigadier argument type for {@code arg}. A greedy final {@link ArgType#STRING} reads the rest of the input
     * (spaces included) through {@link StringArgumentType#greedyString()}; because Brigadier can only capture the
     * remainder on a terminal node, a greedy flag on any non-last argument is ignored and falls back to a word. A
     * numeric argument that declares a bound builds the bounded node, so an out-of-range value is a syntax error.
     */
    private static ArgumentType<?> argumentType(ArgumentSpec arg, boolean last) {
        if (last && arg.greedy() && arg.type() == ArgType.STRING) {
            return StringArgumentType.greedyString();
        }
        return switch (arg.type()) {
            case INT ->
                arg.min().isEmpty() && arg.max().isEmpty()
                        ? IntegerArgumentType.integer()
                        : IntegerArgumentType.integer(
                                arg.min().map(Double::intValue).orElse(Integer.MIN_VALUE),
                                arg.max().map(Double::intValue).orElse(Integer.MAX_VALUE));
            case DOUBLE ->
                arg.min().isEmpty() && arg.max().isEmpty()
                        ? DoubleArgumentType.doubleArg()
                        : DoubleArgumentType.doubleArg(
                                arg.min().orElse(-Double.MAX_VALUE), arg.max().orElse(Double.MAX_VALUE));
            case BOOL -> BoolArgumentType.bool();
            case ONLINE_PLAYER -> ArgumentTypes.player();
            case STRING, PLAYER, MATERIAL, WORLD -> StringArgumentType.word();
        };
    }

    /**
     * The suggestion provider for a word-typed argument, or empty when the argument type supplies its own
     * completion. {@code ONLINE_PLAYER} autocompletes online players through {@link ArgumentTypes#player()}, so it
     * needs no extra provider; the offline {@code PLAYER} kind stays a plain word but still surfaces online names.
     */
    private static Optional<SuggestionProvider<CommandSourceStack>> suggestionsFor(ArgType type) {
        return switch (type) {
            case PLAYER -> Optional.of(CommandSuggestions.onlinePlayers());
            case MATERIAL -> Optional.of(materialSuggestions());
            case WORLD -> Optional.of(worldSuggestions());
            case STRING, INT, DOUBLE, BOOL, ONLINE_PLAYER -> Optional.empty();
        };
    }

    /** Suggest lowercased material names, prefix-filtered by the partial token; read-only, tick-thread safe. */
    private static SuggestionProvider<CommandSourceStack> materialSuggestions() {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (Material material : Material.values()) {
                String name = material.name().toLowerCase(Locale.ROOT);
                if (prefix.isEmpty() || name.startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    /** Suggest the loaded world names, prefix-filtered by the partial token; read-only, tick-thread safe. */
    private static SuggestionProvider<CommandSourceStack> worldSuggestions() {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (World world : Bukkit.getWorlds()) {
                String name = world.getName();
                if (prefix.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    /** Render one parsed argument to the text a placeholder shows for it. */
    private static String stringify(CommandContext<CommandSourceStack> ctx, ArgumentSpec arg)
            throws CommandSyntaxException {
        String name = arg.name();
        return switch (arg.type()) {
            case INT -> Integer.toString(IntegerArgumentType.getInteger(ctx, name));
            case DOUBLE -> Double.toString(DoubleArgumentType.getDouble(ctx, name));
            case BOOL -> Boolean.toString(BoolArgumentType.getBool(ctx, name));
            case ONLINE_PLAYER -> resolvePlayerName(ctx, name);
            case STRING, PLAYER, MATERIAL, WORLD -> StringArgumentType.getString(ctx, name);
        };
    }

    /** Resolve an online-player argument to the target's name, empty when the selector matched nobody. */
    private static String resolvePlayerName(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument(name, PlayerSelectorArgumentResolver.class);
        List<Player> resolved = resolver.resolve(ctx.getSource());
        return resolved.isEmpty() ? "" : resolved.get(0).getName();
    }
}
