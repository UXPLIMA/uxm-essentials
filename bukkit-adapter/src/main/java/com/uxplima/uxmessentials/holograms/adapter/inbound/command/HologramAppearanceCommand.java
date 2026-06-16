package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
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
import com.uxplima.uxmessentials.holograms.domain.TextAlignment;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The FancyHolograms-style styling subcommands under {@code /hologram}: {@code billboard}, {@code background},
 * {@code shadow}, {@code brightness}, {@code scale}, {@code linewidth}, {@code viewrange}, {@code alignment},
 * {@code seethrough}, {@code translation}, {@code shadowradius}, {@code shadowstrength} and
 * {@code refreshinterval}. Each maps its parsed argument to one {@link Appearance} (or refresh) transition on
 * the shared {@link HologramServices}; collected here so the main {@code /hologram} command class stays focused
 * on its create/edit/move forms while keeping the single literal intact. The raw value is parsed and clamped
 * here so an invalid value is rejected at the boundary (a clear {@link HologramsMessageKey} reply), never inside
 * a use case and never a crashed render.
 *
 * <p>Every {@code name} argument completes against the current hologram names, and the {@code billboard} and
 * {@code alignment} value arguments complete against their fixed enum values; boolean arguments complete
 * against {@code true}/{@code false} through Brigadier's native bool suggestions.
 */
@NullMarked
final class HologramAppearanceCommand extends HologramCommandSupport {

    private static final List<String> BILLBOARDS = List.of("CENTER", "FIXED", "VERTICAL", "HORIZONTAL");
    private static final List<String> ALIGNMENTS = List.of("LEFT", "CENTER", "RIGHT");

    HologramAppearanceCommand(
            HologramServices services, Messages messages, Supplier<? extends Collection<String>> hologramNames) {
        super(services, messages, hologramNames);
    }

    /** The styling subcommand nodes the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(
                choiceValueNode("billboard", BILLBOARDS, this::billboard),
                valueNode("background", StringArgumentType.greedyString(), this::background),
                valueNode("shadow", BoolArgumentType.bool(), this::shadow),
                brightnessNode(),
                scaleNode(),
                valueNode("linewidth", IntegerArgumentType.integer(), this::lineWidth),
                valueNode("viewrange", FloatArgumentType.floatArg(), this::viewRange),
                choiceValueNode("alignment", ALIGNMENTS, this::alignment),
                valueNode("seethrough", BoolArgumentType.bool(), this::seeThrough),
                translationNode(),
                valueNode("shadowradius", FloatArgumentType.floatArg(), this::shadowRadius),
                valueNode("shadowstrength", FloatArgumentType.floatArg(), this::shadowStrength),
                valueNode("refreshinterval", IntegerArgumentType.integer(), this::refreshInterval));
    }

    private LiteralArgumentBuilder<CommandSourceStack> valueNode(
            String literal,
            com.mojang.brigadier.arguments.ArgumentType<?> valueType,
            Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(nameArgument("name")
                        .then(Commands.argument("value", valueType).executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> choiceValueNode(
            String literal, List<String> choices, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(nameArgument("name").then(choiceArgument("value", choices).executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> brightnessNode() {
        return Commands.literal("brightness")
                .then(nameArgument("name")
                        .then(Commands.argument("block", IntegerArgumentType.integer(0, 15))
                                .then(Commands.argument("sky", IntegerArgumentType.integer(0, 15))
                                        .executes(this::brightness))));
    }

    /** {@code scale <name> <x> [y] [z]} — one value scales uniformly, three set each axis independently. */
    private LiteralArgumentBuilder<CommandSourceStack> scaleNode() {
        return Commands.literal("scale")
                .then(nameArgument("name")
                        .then(Commands.argument("x", FloatArgumentType.floatArg())
                                .executes(this::scale)
                                .then(Commands.argument("y", FloatArgumentType.floatArg())
                                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                .executes(this::scalePerAxis)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> translationNode() {
        return Commands.literal("translation")
                .then(nameArgument("name")
                        .then(Commands.argument("x", FloatArgumentType.floatArg())
                                .then(Commands.argument("y", FloatArgumentType.floatArg())
                                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                .executes(this::translation)))));
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
        float scale = Appearance.clampScale(ctx.getArgument("x", Float.class));
        return applyAppearance(ctx, sender, current -> current.withScale(scale));
    }

    private int scalePerAxis(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        float x = Appearance.clampScale(ctx.getArgument("x", Float.class));
        float y = Appearance.clampScale(ctx.getArgument("y", Float.class));
        float z = Appearance.clampScale(ctx.getArgument("z", Float.class));
        return applyAppearance(ctx, sender, current -> current.withScale(x, y, z));
    }

    private int translation(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        float x = Appearance.clampTranslation(ctx.getArgument("x", Float.class));
        float y = Appearance.clampTranslation(ctx.getArgument("y", Float.class));
        float z = Appearance.clampTranslation(ctx.getArgument("z", Float.class));
        return applyAppearance(ctx, sender, current -> current.withTranslation(x, y, z));
    }

    private int alignment(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<TextAlignment> alignment = TextAlignment.parse(ctx.getArgument("value", String.class));
        if (alignment.isEmpty()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_ALIGNMENT_INVALID, Map.of());
            return 0;
        }
        return applyAppearance(ctx, sender, current -> current.withAlignment(alignment.orElseThrow()));
    }

    private int seeThrough(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        boolean seeThrough = ctx.getArgument("value", Boolean.class);
        return applyAppearance(ctx, sender, current -> current.withSeeThrough(seeThrough));
    }

    private int shadowRadius(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        float radius = Appearance.clampShadow(ctx.getArgument("value", Float.class));
        return applyAppearance(ctx, sender, current -> current.withShadowRadius(radius));
    }

    private int shadowStrength(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        float strength = Appearance.clampShadow(ctx.getArgument("value", Float.class));
        return applyAppearance(ctx, sender, current -> current.withShadowStrength(strength));
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
