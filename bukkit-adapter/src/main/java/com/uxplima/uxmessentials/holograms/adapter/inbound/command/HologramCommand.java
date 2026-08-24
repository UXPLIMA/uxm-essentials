package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramListMenu;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /hologram <create|delete|list|addline|setline|removeline|movehere|item|block|…>}: the single operator
 * command for native-Display holograms. Each subcommand maps its arguments to one use-case call; the
 * create/move forms read the operator's current position, the line indices are 1-based at the command boundary
 * (converted to 0-based for the use cases), and {@code item}/{@code block} switch an existing hologram to a
 * floating item or block. The base {@code uxmessentials.hologram.use} node guards the whole command, and each verb
 * additionally runs behind the capability node its work belongs to ({@code uxmessentials.hologram.create},
 * {@code .delete}, {@code .move}, {@code .appearance}, {@code .visibility}, {@code .action}, {@code .view},
 * {@code .edit}), every one of which defaults to allowed so an existing grant is unchanged until an operator
 * negates one.
 */
@NullMarked
public final class HologramCommand extends HologramCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.hologram.use";
    private static final String GUI_PERMISSION = "uxmessentials.holograms.gui";

    private static final String CREATE = "uxmessentials.hologram.create";
    private static final String DELETE = "uxmessentials.hologram.delete";
    private static final String MOVE = "uxmessentials.hologram.move";
    private static final String APPEARANCE = "uxmessentials.hologram.appearance";
    private static final String VISIBILITY = "uxmessentials.hologram.visibility";
    private static final String ACTION = "uxmessentials.hologram.action";
    private static final String VIEW = "uxmessentials.hologram.view";
    private static final String EDIT = "uxmessentials.hologram.edit";

    /**
     * Which capability each verb belongs to, so the many verbs of {@code /hologram} are grantable in the shapes an
     * operator actually thinks in rather than one at a time. A verb not named here counts as {@link #EDIT}, the
     * content editing every hologram command ultimately serves, which keeps a newly added verb gated rather than
     * open.
     */
    private static final Map<String, String> CAPABILITIES = Map.ofEntries(
            Map.entry("create", CREATE),
            Map.entry("createat", CREATE),
            Map.entry("copy", CREATE),
            Map.entry("delete", DELETE),
            Map.entry("list", VIEW),
            Map.entry("info", VIEW),
            Map.entry("nearby", VIEW),
            Map.entry("movehere", MOVE),
            Map.entry("move", MOVE),
            Map.entry("moveto", MOVE),
            Map.entry("moveat", MOVE),
            Map.entry("center", MOVE),
            Map.entry("teleport", MOVE),
            Map.entry("rotate", MOVE),
            Map.entry("billboard", APPEARANCE),
            Map.entry("background", APPEARANCE),
            Map.entry("glow", APPEARANCE),
            Map.entry("opacity", APPEARANCE),
            Map.entry("shadow", APPEARANCE),
            Map.entry("shadowradius", APPEARANCE),
            Map.entry("shadowstrength", APPEARANCE),
            Map.entry("linewidth", APPEARANCE),
            Map.entry("viewrange", APPEARANCE),
            Map.entry("alignment", APPEARANCE),
            Map.entry("seethrough", APPEARANCE),
            Map.entry("growup", APPEARANCE),
            Map.entry("item", APPEARANCE),
            Map.entry("block", APPEARANCE),
            Map.entry("head", APPEARANCE),
            Map.entry("entity", APPEARANCE),
            Map.entry("visibility", VISIBILITY),
            Map.entry("visibilitydistance", VISIBILITY),
            Map.entry("show", VISIBILITY),
            Map.entry("hide", VISIBILITY),
            Map.entry("blacklist", VISIBILITY),
            Map.entry("unblacklist", VISIBILITY),
            Map.entry("action", ACTION),
            Map.entry("clickcommand", ACTION));

    private final Supplier<? extends Collection<String>> hologramNames;
    private final Supplier<? extends Collection<String>> npcNames;
    private final HologramListMenu listMenu;

    public HologramCommand(
            HologramServices services,
            Messages messages,
            Supplier<? extends Collection<String>> hologramNames,
            Supplier<? extends Collection<String>> npcNames,
            HologramListMenu listMenu) {
        super(services, messages, hologramNames);
        this.hologramNames = hologramNames;
        this.npcNames = Objects.requireNonNull(npcNames, "npcNames");
        this.listMenu = Objects.requireNonNull(listMenu, "listMenu");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("hologram")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::openGui);
        List<LiteralArgumentBuilder<CommandSourceStack>> verbs = new ArrayList<>(List.of(
                createNode(),
                createAtNode(),
                name("delete", this::delete),
                Commands.literal("list").executes(this::list),
                textNode("addline", this::addLine),
                setLineNode(),
                indexNode("removeline", this::removeLine),
                name("movehere", this::move),
                moveNode(),
                moveToNode(),
                moveAtNode()));
        verbs.addAll(new HologramAppearanceCommand(services, messages, hologramNames).nodes());
        verbs.addAll(new HologramVisibilityCommand(services, messages, hologramNames).nodes());
        verbs.addAll(new HologramModelCommand(services, messages, hologramNames).nodes());
        verbs.addAll(new HologramConvenienceCommand(services, messages, hologramNames).nodes());
        verbs.addAll(new HologramNpcCommand(services, messages, hologramNames, npcNames).nodes());
        verbs.addAll(new HologramPageCommand(services, messages, hologramNames).nodes());
        verbs.addAll(new HologramActionCommand(services, messages, hologramNames).nodes());
        for (LiteralArgumentBuilder<CommandSourceStack> verb : verbs) {
            root.then(verb.requires(capability(verb.getLiteral())));
        }
        return root.build();
    }

    /**
     * The gate one verb runs behind: the base node, then the capability node its verb belongs to. Every capability
     * node defaults to allowed, so an existing {@code uxmessentials.hologram.use} grant keeps the whole command and
     * an operator narrows it by negating one capability: a builder who may re-style and move a hologram but never
     * delete one holds the base node with {@code uxmessentials.hologram.delete} negated.
     */
    private static Predicate<CommandSourceStack> capability(String verb) {
        String node = CAPABILITIES.getOrDefault(verb, EDIT);
        return src ->
                src.getSender().hasPermission(PERMISSION) && src.getSender().hasPermission(node);
    }

    @Override
    public String description() {
        return "Create and manage native-Display holograms.";
    }

    private LiteralArgumentBuilder<CommandSourceStack> createNode() {
        // The create name is a brand-new name, so it deliberately does not complete against the existing set.
        return Commands.literal("create")
                .executes(ctx -> usage(ctx, "hologram create", "<name> <text>", "Create a new hologram"))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> usage(ctx, "hologram create", "<name> <text>", "Create a new hologram"))
                        .then(Commands.literal("at").then(createAtArguments()))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::create)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> textNode(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .executes(ctx -> usage(ctx, "hologram " + literal, "<name> <text>", "Hologram " + literal))
                .then(nameArgument("name")
                        .executes(ctx -> usage(ctx, "hologram " + literal, "<name> <text>", "Hologram " + literal))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> setLineNode() {
        return Commands.literal("setline")
                .executes(ctx -> usage(ctx, "hologram setline", "<name> <index> <text>", "Set hologram line"))
                .then(nameArgument("name")
                        .executes(ctx -> usage(ctx, "hologram setline", "<name> <index> <text>", "Set hologram line"))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx ->
                                        usage(ctx, "hologram setline", "<name> <index> <text>", "Set hologram line"))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(this::setLine))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> indexNode(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .executes(ctx -> usage(ctx, "hologram " + literal, "<name> <index>", "Hologram " + literal))
                .then(nameArgument("name")
                        .executes(ctx -> usage(ctx, "hologram " + literal, "<name> <index>", "Hologram " + literal))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> name(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .executes(ctx -> usage(ctx, "hologram " + literal, "<name>", "Hologram " + literal))
                .then(nameArgument("name").executes(action));
    }

    /**
     * {@code /hologram} with no arguments: open the management GUI for a player who holds the GUI node, else
     * print the {@code /hologram list} text so a console or an operator without the GUI node still gets a useful
     * reply. The GUI node is checked through the live sender's permissions, the same gate the {@code /uxmess gui}
     * hub entry uses.
     */
    private int openGui(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) {
            services.list().list(actor(ctx));
            return Command.SINGLE_SUCCESS;
        }
        if (sender.hasPermission(GUI_PERMISSION)) {
            listMenu.open(ref(sender));
        } else {
            services.list().list(ref(sender));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.create().create(ref(sender), nameArg(ctx), position(sender), HologramLine.of(text(ctx)));
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        services.delete().delete(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        services.list().list(actor(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int addLine(CommandContext<CommandSourceStack> ctx) {
        services.addLine().add(actor(ctx), nameArg(ctx), HologramLine.of(text(ctx)));
        return Command.SINGLE_SUCCESS;
    }

    private int setLine(CommandContext<CommandSourceStack> ctx) {
        services.setLine().set(actor(ctx), nameArg(ctx), zeroBasedIndex(ctx), HologramLine.of(text(ctx)));
        return Command.SINGLE_SUCCESS;
    }

    private int removeLine(CommandContext<CommandSourceStack> ctx) {
        services.removeLine().remove(actor(ctx), nameArg(ctx), zeroBasedIndex(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int move(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.move().move(ref(sender), nameArg(ctx), position(sender));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> moveToNode() {
        return Commands.literal("moveto")
                .executes(ctx -> usage(ctx, "hologram moveto", "<name> <x> <y> <z>", "Move hologram to coordinates"))
                .then(nameArgument("name")
                        .executes(ctx ->
                                usage(ctx, "hologram moveto", "<name> <x> <y> <z>", "Move hologram to coordinates"))
                        .then(Commands.argument("x", StringArgumentType.word())
                                .executes(ctx -> usage(
                                        ctx, "hologram moveto", "<name> <x> <y> <z>", "Move hologram to coordinates"))
                                .then(Commands.argument("y", StringArgumentType.word())
                                        .executes(ctx -> usage(
                                                ctx,
                                                "hologram moveto",
                                                "<name> <x> <y> <z>",
                                                "Move hologram to coordinates"))
                                        .then(Commands.argument("z", StringArgumentType.word())
                                                .executes(this::moveTo)))));
    }

    private int moveTo(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Position base = position(sender);
        Double x = resolveCoord(ctx.getArgument("x", String.class), base.x());
        Double y = resolveCoord(ctx.getArgument("y", String.class), base.y());
        Double z = resolveCoord(ctx.getArgument("z", String.class), base.z());
        if (x == null || y == null || z == null) {
            feedback.send(
                    sender,
                    HologramsMessageKey.HOLOGRAM_INVALID_COORDS,
                    Map.of(
                            "coords",
                            ctx.getArgument("x", String.class) + " " + ctx.getArgument("y", String.class) + " "
                                    + ctx.getArgument("z", String.class)));
            return 0;
        }
        services.move().move(ref(sender), nameArg(ctx), Position.of(base.world(), x, y, z));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> createAtNode() {
        return Commands.literal("createat")
                .executes(ctx -> usage(
                        ctx,
                        "hologram createat",
                        "<name> <world> <x> <y> <z> <text>",
                        "Create a hologram at an explicit location"))
                .then(Commands.argument("name", StringArgumentType.string()).then(createAtArguments()));
    }

    private LiteralArgumentBuilder<CommandSourceStack> moveNode() {
        return Commands.literal("move")
                .executes(ctx -> usage(ctx, "hologram move", "<name> [at <world> <x> <y> <z>]", "Move a hologram"))
                .then(nameArgument("name")
                        .executes(this::move)
                        .then(Commands.literal("at").then(moveAtArguments())));
    }

    private LiteralArgumentBuilder<CommandSourceStack> moveAtNode() {
        return Commands.literal("moveat")
                .executes(ctx -> usage(
                        ctx,
                        "hologram moveat",
                        "<name> <world> <x> <y> <z>",
                        "Move a hologram to an explicit location"))
                .then(nameArgument("name").then(moveAtArguments()));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> createAtArguments() {
        return explicitPositionArguments(this::createAt, true);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> moveAtArguments() {
        return explicitPositionArguments(this::moveAt, false);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> explicitPositionArguments(
            Command<CommandSourceStack> action, boolean textRequired) {
        RequiredArgumentBuilder<CommandSourceStack, Double> z = Commands.argument("z", DoubleArgumentType.doubleArg());
        if (textRequired) {
            z.then(Commands.argument("text", StringArgumentType.greedyString()).executes(action));
        } else {
            z.executes(action);
        }
        return Commands.argument("world", StringArgumentType.word())
                .suggests(com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions.loadedWorlds())
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(z)));
    }

    private int createAt(CommandContext<CommandSourceStack> ctx) {
        Position at = explicitPosition(ctx);
        if (at == null) {
            return 0;
        }
        services.create().create(actor(ctx), nameArg(ctx), at, HologramLine.of(text(ctx)));
        return Command.SINGLE_SUCCESS;
    }

    private int moveAt(CommandContext<CommandSourceStack> ctx) {
        Position at = explicitPosition(ctx);
        if (at == null) {
            return 0;
        }
        services.move().move(actor(ctx), nameArg(ctx), at);
        return Command.SINGLE_SUCCESS;
    }

    private @Nullable Position explicitPosition(CommandContext<CommandSourceStack> ctx) {
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
        World world = sender.getServer().getWorld(ctx.getArgument("world", String.class));
        double x = ctx.getArgument("x", Double.class);
        double y = ctx.getArgument("y", Double.class);
        double z = ctx.getArgument("z", Double.class);
        if (world == null) {
            feedback.send(
                    sender,
                    com.uxplima.uxmessentials.shared.application.message.SharedMessageKey.COMMAND_UNKNOWN_WORLD,
                    Map.of("world", ctx.getArgument("world", String.class)));
            return null;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_INVALID_COORDS, Map.of("coords", explicitCoords(ctx)));
            return null;
        }
        return Position.of(BukkitRefs.toRef(world), x, y, z);
    }

    private static String explicitCoords(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument("world", String.class) + " " + ctx.getArgument("x", Double.class) + " "
                + ctx.getArgument("y", Double.class) + " " + ctx.getArgument("z", Double.class);
    }

    /**
     * Resolve a coordinate token relative to {@code base}: {@code ~} is the base, {@code ~n} is the base plus
     * {@code n}, anything else is an absolute value; {@code null} when the token is not a finite number.
     */
    static @Nullable Double resolveCoord(String token, double base) {
        String t = token.strip();
        try {
            double value;
            if (t.equals("~")) {
                value = base;
            } else if (t.startsWith("~")) {
                value = base + Double.parseDouble(t.substring(1));
            } else {
                value = Double.parseDouble(t);
            }
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static HologramName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HologramName.of(ctx.getArgument("name", String.class));
    }

    private static String text(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument("text", String.class);
    }

    private static int zeroBasedIndex(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument("index", Integer.class) - 1;
    }
}
