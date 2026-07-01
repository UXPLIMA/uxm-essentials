package com.uxplima.uxmessentials.custommenus.adapter.inbound.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
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
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.ArgumentSpec.ArgType;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The operator-declared open command for one custom menu: a menu's {@code command {}} block ({@code /shop},
 * {@code /store}) opens that menu's spec through the public {@link Menus} facade, exactly as {@code /menu open
 * <id>} does, but with the menu's own name, aliases, permission gate, deny message and typed arguments.
 *
 * <p>Unlike {@code /menu}, the permission is checked inside the executor rather than as a Brigadier {@code requires}
 * gate: a {@code requires} gate would hide the command from a sender who lacks the node, giving an "unknown command"
 * rather than the operator's configured deny message. So the command is always visible and the executor decides —
 * a console sender is turned away unless the block allows it, a missing permission draws the deny message (or the
 * shared no-permission line when none is configured), and only then does a real player open the menu.
 *
 * <p>A command that declares {@code arguments} carries one typed Brigadier node per argument, chained in order
 * under the literal so every argument is required (the deepest node carries the executor). Each node completes
 * against its type — an online player, the material or loaded-world names, the online roster — so an operator gets
 * autocomplete for free, and a wrong-typed value ({@code /gift Steve xyz} where {@code amount} is an int) is
 * rejected as a syntax error before the menu opens. The parsed values reach the menu as {@code %argument_<name>%}
 * placeholders, keyed by argument name in open order.
 */
@NullMarked
public final class MenuOpenCommand implements CommandRegistration {

    private final Menus menus;
    private final String menuId;
    private final OpenCommandSpec spec;
    private final CommandFeedback feedback;

    public MenuOpenCommand(Menus menus, String menuId, OpenCommandSpec spec, Messages messages) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.menuId = Objects.requireNonNull(menuId, "menuId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(spec.name());
        if (spec.arguments().isEmpty()) {
            return literal.executes(this::open).build();
        }
        return literal.then(argumentChain(spec.arguments())).build();
    }

    @Override
    public List<String> aliases() {
        return spec.aliases();
    }

    @Override
    public String description() {
        return spec.usage().orElseGet(() -> "Opens the " + menuId + " custom menu.");
    }

    /**
     * Nest the typed argument nodes for {@code args} innermost-first, so the deepest node carries the executor and
     * every shallower node only forwards to the next — making all arguments required in declared order.
     */
    private ArgumentBuilder<CommandSourceStack, ?> argumentChain(List<ArgumentSpec> args) {
        int last = args.size() - 1;
        RequiredArgumentBuilder<CommandSourceStack, ?> node = argumentNode(args.get(last), true);
        node.executes(this::open);
        for (int i = last - 1; i >= 0; i--) {
            node = argumentNode(args.get(i), false).then(node);
        }
        return node;
    }

    /** Build one typed Brigadier argument node with its autocomplete for {@code arg}; {@code last} gates greedy capture. */
    private RequiredArgumentBuilder<CommandSourceStack, ?> argumentNode(ArgumentSpec arg, boolean last) {
        RequiredArgumentBuilder<CommandSourceStack, ?> node = Commands.argument(arg.name(), argumentType(arg, last));
        suggestionsFor(arg.type()).ifPresent(node::suggests);
        return node;
    }

    /**
     * The Brigadier argument type for {@code arg}. A greedy final {@link ArgType#STRING} reads the rest of the input
     * (spaces included) through {@link StringArgumentType#greedyString()}; because Brigadier can only capture the
     * remainder on a terminal node, a greedy flag on any non-last argument is ignored and falls back to a word.
     */
    private static ArgumentType<?> argumentType(ArgumentSpec arg, boolean last) {
        if (last && arg.greedy() && arg.type() == ArgType.STRING) {
            return StringArgumentType.greedyString();
        }
        return argumentType(arg.type());
    }

    /** The Brigadier argument type for {@code type}; the word-typed kinds ({@code PLAYER}/material/world) suggest separately. */
    private static ArgumentType<?> argumentType(ArgType type) {
        return switch (type) {
            case INT -> IntegerArgumentType.integer();
            case DOUBLE -> DoubleArgumentType.doubleArg();
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

    private int open(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        // A non-player sender is turned away first unless the block explicitly allows the console.
        if (!(sender instanceof Player) && !spec.consoleAllowed()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_CONSOLE_DENIED);
            return 0;
        }
        if (spec.permission().isPresent()
                && !sender.hasPermission(spec.permission().get())) {
            denyPermission(sender);
            return 0;
        }
        // Opening a menu needs a player window: a console that cleared the gate above has nobody to open for.
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        // Pre-open actions ride the engine's open-actions seam: opening the menu below runs its open-actions on open,
        // so a menu whose command opens it fires that menu's open-actions — no separate pre-open hook needed here.
        Map<String, String> arguments = collectArguments(ctx, spec.arguments());
        menus.open(BukkitRefs.toRef(player), menuId, null, 0, arguments);
        return Command.SINGLE_SUCCESS;
    }

    /** Read every declared argument off {@code ctx}, keyed by name in declared order, as its rendered text. */
    private static Map<String, String> collectArguments(
            CommandContext<CommandSourceStack> ctx, List<ArgumentSpec> specs) throws CommandSyntaxException {
        Map<String, String> values = new LinkedHashMap<>();
        for (ArgumentSpec arg : specs) {
            values.put(arg.name(), stringifyArgument(ctx, arg));
        }
        return values;
    }

    /** Render one parsed argument to the text a {@code %argument_<name>%} placeholder shows for it. */
    private static String stringifyArgument(CommandContext<CommandSourceStack> ctx, ArgumentSpec arg)
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

    /** Reject a failed permission check with the operator's configured deny line, or the shared default when none. */
    private void denyPermission(CommandSender sender) {
        spec.denyMessage()
                .ifPresentOrElse(
                        line -> sender.sendMessage(StyledText.render(line)),
                        () -> feedback.send(sender, SharedMessageKey.COMMAND_NO_PERMISSION));
    }
}
