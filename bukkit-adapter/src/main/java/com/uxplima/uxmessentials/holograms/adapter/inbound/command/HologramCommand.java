package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

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
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /hologram <create|delete|list|addline|setline|removeline|movehere>}: the single operator command for
 * native-Display holograms. Each subcommand maps its arguments to one use-case call; the create/move forms
 * read the operator's current position, and the line indices are 1-based at the command boundary (converted
 * to 0-based for the use cases). The base {@code uxmessentials.hologram.use} node guards the whole command.
 */
@NullMarked
public final class HologramCommand extends HologramCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.hologram.use";

    public HologramCommand(HologramServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("hologram")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(createNode())
                .then(name("delete", this::delete))
                .then(Commands.literal("list").executes(this::list))
                .then(textNode("addline", this::addLine))
                .then(setLineNode())
                .then(indexNode("removeline", this::removeLine))
                .then(name("movehere", this::move));
        for (LiteralArgumentBuilder<CommandSourceStack> styling :
                new HologramAppearanceCommand(services, messages).nodes()) {
            root.then(styling);
        }
        for (LiteralArgumentBuilder<CommandSourceStack> visibility :
                new HologramVisibilityCommand(services, messages).nodes()) {
            root.then(visibility);
        }
        return root.build();
    }

    @Override
    public String description() {
        return "Create and manage native-Display holograms.";
    }

    private LiteralArgumentBuilder<CommandSourceStack> createNode() {
        return Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::create)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> textNode(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> setLineNode() {
        return Commands.literal("setline")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(this::setLine))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> indexNode(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> name(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.word()).executes(action));
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
