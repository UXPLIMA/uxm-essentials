package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.NearbyHolograms;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The hologram convenience subcommands under {@code /hologram}: {@code copy <src> <dest>} duplicates
 * a hologram, {@code info <name>} prints its properties, {@code nearby [radius]} lists holograms around the
 * operator, {@code center <name>} snaps a hologram to its block centre, {@code teleport <name>} sends the
 * operator to a hologram, {@code rotate <name> <yaw> [pitch]} sets a stored display rotation, and {@code
 * insertline <name> <index> <text…>} inserts a line before a 1-based position. Collected here so the main {@code
 * /hologram} command class stays focused while keeping the single literal intact.
 */
@NullMarked
final class HologramConvenienceCommand extends HologramCommandSupport {

    HologramConvenienceCommand(
            HologramServices services, Messages messages, Supplier<? extends Collection<String>> hologramNames) {
        super(services, messages, hologramNames);
    }

    /** The convenience subcommand nodes the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(
                copyNode(),
                name("info", this::info),
                nearbyNode(),
                name("center", this::center),
                name("teleport", this::teleport),
                rotateNode(),
                insertLineNode(),
                clickCommandNode(),
                leaderboardNode());
    }

    private static final int DEFAULT_LEADERBOARD_LIMIT = 10;

    private LiteralArgumentBuilder<CommandSourceStack> leaderboardNode() {
        return Commands.literal("leaderboard")
                .then(nameArgument("name")
                        .then(Commands.argument("provider", StringArgumentType.word())
                                .executes(ctx -> leaderboard(ctx, DEFAULT_LEADERBOARD_LIMIT))
                                .then(Commands.argument(
                                                "limit",
                                                IntegerArgumentType.integer(
                                                        1,
                                                        com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec
                                                                .MAX_LIMIT))
                                        .executes(ctx -> leaderboard(ctx, ctx.getArgument("limit", Integer.class))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> clickCommandNode() {
        return Commands.literal("clickcommand")
                .then(nameArgument("name")
                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(this::clickCommand)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> copyNode() {
        // The source completes against the existing names; the destination is a brand-new name, so it does not.
        return Commands.literal("copy")
                .then(nameArgument("name")
                        .then(Commands.argument("dest", StringArgumentType.word())
                                .executes(this::copy)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> nearbyNode() {
        return Commands.literal("nearby")
                .executes(this::nearby)
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(this::nearby));
    }

    private LiteralArgumentBuilder<CommandSourceStack> rotateNode() {
        return Commands.literal("rotate")
                .then(nameArgument("name")
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                .executes(this::rotate)
                                .then(Commands.argument("pitch", FloatArgumentType.floatArg())
                                        .executes(this::rotate))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> insertLineNode() {
        return Commands.literal("insertline")
                .then(nameArgument("name")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(this::insertLine))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> name(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal).then(nameArgument("name").executes(action));
    }

    private int copy(CommandContext<CommandSourceStack> ctx) {
        HologramName dest = HologramName.of(ctx.getArgument("dest", String.class));
        services.copy().copy(actor(ctx), nameArg(ctx), dest);
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        services.info().describe(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int nearby(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.nearby().nearby(ref(sender), position(sender), radius(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int center(CommandContext<CommandSourceStack> ctx) {
        services.center().center(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.teleport().teleport(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int rotate(CommandContext<CommandSourceStack> ctx) {
        float yaw = ctx.getArgument("yaw", Float.class);
        float pitch = pitchOr(ctx);
        services.rotate().rotate(actor(ctx), nameArg(ctx), Rotation.of(yaw, pitch));
        return Command.SINGLE_SUCCESS;
    }

    private int insertLine(CommandContext<CommandSourceStack> ctx) {
        int index = ctx.getArgument("index", Integer.class) - 1;
        HologramLine line = HologramLine.of(ctx.getArgument("text", String.class));
        services.insertLine().insert(actor(ctx), nameArg(ctx), index, line);
        return Command.SINGLE_SUCCESS;
    }

    private int leaderboard(CommandContext<CommandSourceStack> ctx, int limit) {
        String provider = ctx.getArgument("provider", String.class).strip();
        com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec spec =
                provider.equalsIgnoreCase("none") || provider.equalsIgnoreCase("clear")
                        ? null
                        : new com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec(provider, limit);
        services.leaderboard().set(actor(ctx), nameArg(ctx), spec);
        return Command.SINGLE_SUCCESS;
    }

    private int clickCommand(CommandContext<CommandSourceStack> ctx) {
        String raw = ctx.getArgument("command", String.class).strip();
        // A leading slash is optional; a 'none'/'clear' keyword removes the click action.
        String command = raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("clear")
                ? null
                : (raw.startsWith("/") ? raw.substring(1) : raw);
        services.clickCommand().set(actor(ctx), nameArg(ctx), command);
        return Command.SINGLE_SUCCESS;
    }

    private static HologramName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HologramName.of(ctx.getArgument("name", String.class));
    }

    /** The optional {@code radius} for {@code /hologram nearby}, defaulting when the bare form is used. */
    private static int radius(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getArgument("radius", Integer.class);
        } catch (IllegalArgumentException absent) {
            return NearbyHolograms.DEFAULT_RADIUS;
        }
    }

    /** The optional {@code pitch} for {@code /hologram rotate}, defaulting to 0 when the bare form is used. */
    private static float pitchOr(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getArgument("pitch", Float.class);
        } catch (IllegalArgumentException absent) {
            return 0f;
        }
    }
}
