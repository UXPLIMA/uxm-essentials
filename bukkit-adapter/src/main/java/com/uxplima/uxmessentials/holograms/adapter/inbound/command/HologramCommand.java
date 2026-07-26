package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramListMenu;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /hologram <create|delete|list|addline|setline|removeline|movehere|item|block|…>}: the single operator
 * command for native-Display holograms. Each subcommand maps its arguments to one use-case call; the
 * create/move forms read the operator's current position, the line indices are 1-based at the command boundary
 * (converted to 0-based for the use cases), and {@code item}/{@code block} switch an existing hologram to a
 * floating item or block. The base {@code uxmessentials.hologram.use} node guards the whole command.
 */
@NullMarked
public final class HologramCommand extends HologramCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.hologram.use";
    private static final String GUI_PERMISSION = "uxmessentials.holograms.gui";

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
                .executes(this::openGui)
                .then(createNode())
                .then(name("delete", this::delete))
                .then(Commands.literal("list").executes(this::list))
                .then(textNode("addline", this::addLine))
                .then(setLineNode())
                .then(indexNode("removeline", this::removeLine))
                .then(name("movehere", this::move))
                .then(moveToNode());
        for (LiteralArgumentBuilder<CommandSourceStack> styling :
                new HologramAppearanceCommand(services, messages, hologramNames).nodes()) {
            root.then(styling);
        }
        for (LiteralArgumentBuilder<CommandSourceStack> visibility :
                new HologramVisibilityCommand(services, messages, hologramNames).nodes()) {
            root.then(visibility);
        }
        for (LiteralArgumentBuilder<CommandSourceStack> model :
                new HologramModelCommand(services, messages, hologramNames).nodes()) {
            root.then(model);
        }
        for (LiteralArgumentBuilder<CommandSourceStack> convenience :
                new HologramConvenienceCommand(services, messages, hologramNames).nodes()) {
            root.then(convenience);
        }
        for (LiteralArgumentBuilder<CommandSourceStack> link :
                new HologramNpcCommand(services, messages, hologramNames, npcNames).nodes()) {
            root.then(link);
        }
        for (LiteralArgumentBuilder<CommandSourceStack> page :
                new HologramPageCommand(services, messages, hologramNames).nodes()) {
            root.then(page);
        }
        for (LiteralArgumentBuilder<CommandSourceStack> action :
                new HologramActionCommand(services, messages, hologramNames).nodes()) {
            root.then(action);
        }
        return root.build();
    }

    @Override
    public String description() {
        return "Create and manage native-Display holograms.";
    }

    private LiteralArgumentBuilder<CommandSourceStack> createNode() {
        // The create name is a brand-new name, so it deliberately does not complete against the existing set.
        return Commands.literal("create")
                .executes(ctx -> usage(ctx, "/hologram create <name> <text>"))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> usage(ctx, "/hologram create <name> <text>"))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::create)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> textNode(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .executes(ctx -> usage(ctx, "/hologram " + literal + " <name> <text>"))
                .then(nameArgument("name")
                        .executes(ctx -> usage(ctx, "/hologram " + literal + " <name> <text>"))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> setLineNode() {
        return Commands.literal("setline")
                .executes(ctx -> usage(ctx, "/hologram setline <name> <index> <text>"))
                .then(nameArgument("name")
                        .executes(ctx -> usage(ctx, "/hologram setline <name> <index> <text>"))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> usage(ctx, "/hologram setline <name> <index> <text>"))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(this::setLine))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> indexNode(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .executes(ctx -> usage(ctx, "/hologram " + literal + " <name> <index>"))
                .then(nameArgument("name")
                        .executes(ctx -> usage(ctx, "/hologram " + literal + " <name> <index>"))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> name(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .executes(ctx -> usage(ctx, "/hologram " + literal + " <name>"))
                .then(nameArgument("name").executes(action));
    }

    /**
     * {@code /hologram} with no arguments: open the management GUI for a player who holds the GUI node, else
     * print the {@code /hologram list} text so a console or an operator without the GUI node still gets a useful
     * reply. The GUI node is checked through the live sender's permissions, the same gate the {@code /uxmess gui}
     * hub entry uses.
     */
    private int openGui(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
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
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.delete().delete(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.list().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int addLine(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.addLine().add(ref(sender), nameArg(ctx), HologramLine.of(text(ctx)));
        return Command.SINGLE_SUCCESS;
    }

    private int setLine(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.setLine().set(ref(sender), nameArg(ctx), zeroBasedIndex(ctx), HologramLine.of(text(ctx)));
        return Command.SINGLE_SUCCESS;
    }

    private int removeLine(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.removeLine().remove(ref(sender), nameArg(ctx), zeroBasedIndex(ctx));
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
                .executes(ctx -> usage(ctx, "/hologram moveto <name> <x> <y> <z>"))
                .then(nameArgument("name")
                        .executes(ctx -> usage(ctx, "/hologram moveto <name> <x> <y> <z>"))
                        .then(Commands.argument("x", StringArgumentType.word())
                                .executes(ctx -> usage(ctx, "/hologram moveto <name> <x> <y> <z>"))
                                .then(Commands.argument("y", StringArgumentType.word())
                                        .executes(ctx -> usage(ctx, "/hologram moveto <name> <x> <y> <z>"))
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
