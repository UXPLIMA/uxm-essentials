package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The FancyHolograms-style styling subcommands under {@code /hologram}: {@code billboard}, {@code background},
 * {@code shadow}, {@code brightness}, {@code scale}, {@code linewidth}, {@code viewrange} and
 * {@code refreshinterval}. Each maps its parsed argument to one {@link Appearance} (or refresh) transition on
 * the shared {@link HologramServices}; collected here so the main {@code /hologram} command class stays focused
 * on its create/edit/move forms while keeping the single literal intact. The raw value is parsed and clamped
 * here so an invalid value is rejected at the boundary, never inside a use case.
 */
@NullMarked
final class HologramAppearanceCommand extends HologramCommandSupport {

    HologramAppearanceCommand(HologramServices services, Messages messages) {
        super(services, messages);
    }

    /** The styling subcommand nodes the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(
                valueNode("billboard", StringArgumentType.word(), this::billboard),
                valueNode("background", StringArgumentType.greedyString(), this::background),
                valueNode("shadow", BoolArgumentType.bool(), this::shadow),
                brightnessNode(),
                valueNode("scale", FloatArgumentType.floatArg(), this::scale),
                valueNode("linewidth", IntegerArgumentType.integer(), this::lineWidth),
                valueNode("viewrange", FloatArgumentType.floatArg(), this::viewRange),
                valueNode("refreshinterval", IntegerArgumentType.integer(), this::refreshInterval));
    }

    private LiteralArgumentBuilder<CommandSourceStack> valueNode(
            String literal,
            com.mojang.brigadier.arguments.ArgumentType<?> valueType,
            Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("value", valueType).executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> brightnessNode() {
        return Commands.literal("brightness")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("block", IntegerArgumentType.integer(0, 15))
                                .then(Commands.argument("sky", IntegerArgumentType.integer(0, 15))
                                        .executes(this::brightness))));
    }

    private int billboard(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Billboard> billboard = Billboard.parse(ctx.getArgument("value", String.class));
        if (billboard.isEmpty()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_BILLBOARD_INVALID, Map.of());
            return 0;
        }
        return applyAppearance(ctx, sender, current -> current.withBillboard(billboard.orElseThrow()));
    }

    private int background(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Integer> argb = HologramColors.parse(ctx.getArgument("value", String.class));
        if (argb.isEmpty()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_BACKGROUND_INVALID, Map.of());
            return 0;
        }
        return applyAppearance(ctx, sender, current -> current.withBackgroundArgb(argb.orElseThrow()));
    }

    private int shadow(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        boolean shadow = ctx.getArgument("value", Boolean.class);
        return applyAppearance(ctx, sender, current -> current.withTextShadow(shadow));
    }

    private int brightness(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        int block = ctx.getArgument("block", Integer.class);
        int sky = ctx.getArgument("sky", Integer.class);
        return applyAppearance(ctx, sender, current -> current.withBrightness(block, sky));
    }

    private int scale(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        float scale = Appearance.clampScale(ctx.getArgument("value", Float.class));
        return applyAppearance(ctx, sender, current -> current.withScale(scale));
    }

    private int lineWidth(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        int width = Appearance.clampLineWidth(ctx.getArgument("value", Integer.class));
        return applyAppearance(ctx, sender, current -> current.withLineWidth(width));
    }

    private int viewRange(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        float range = Appearance.clampViewRange(ctx.getArgument("value", Float.class));
        return applyAppearance(ctx, sender, current -> current.withViewRange(range));
    }

    private int refreshInterval(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        int ticks = ctx.getArgument("value", Integer.class);
        services.refresh().set(ref(sender), nameArg(ctx), ticks);
        return Command.SINGLE_SUCCESS;
    }

    private int applyAppearance(
            CommandContext<CommandSourceStack> ctx, Player sender, UnaryOperator<Appearance> mutation) {
        services.appearance().apply(ref(sender), nameArg(ctx), mutation);
        return Command.SINGLE_SUCCESS;
    }

    private static HologramName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HologramName.of(ctx.getArgument("name", String.class));
    }
}
