package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.SetNpcRange;
import com.uxplima.uxmessentials.npc.application.SetNpcState;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The FancyNpcs-parity subcommands under {@code /npc} that the appearance/skin/data/action handlers do not own:
 * {@code moveto} (explicit coordinates), {@code displayname}, {@code cooldown}, {@code mirror}, {@code collidable},
 * {@code showintab}, {@code viewdistance}, {@code turndistance}, {@code state} ({@code on_fire|invisible|silent}),
 * and {@code skinslim}. Every {@code name} word completes against the current NPC names and every choice/boolean
 * argument suggests its values. Collected here so the root {@code /npc} command stays focused while keeping the
 * single literal intact (each contributes argument nodes, never a new literal).
 */
@NullMarked
final class NpcStateCommands extends NpcCommandSupport {

    /** The {@code state} names suggested for {@code /npc state <name> <state>}. */
    private static final List<String> STATE_WORDS = List.of("on_fire", "invisible", "silent");
    /** The keyword that resets a per-NPC distance or cooldown override to the module default. */
    private static final String DEFAULT_KEYWORD = "default";

    NpcStateCommands(NpcServices services, Supplier<? extends Collection<String>> npcNames, Messages messages) {
        super(services, npcNames, messages);
    }

    /** The state subcommand nodes the {@code /npc} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(
                moveToNode(),
                displayNameNode(),
                cooldownNode(),
                bool("mirror", this::mirror),
                bool("collidable", this::collidable),
                bool("showintab", this::showInTab),
                distanceNode("viewdistance", SetNpcRange.Kind.VIEW),
                distanceNode("turndistance", SetNpcRange.Kind.TURN),
                stateNode(),
                bool("skinslim", this::skinSlim));
    }

    private LiteralArgumentBuilder<CommandSourceStack> moveToNode() {
        return Commands.literal("moveto")
                .then(nameArgument()
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> moveTo(ctx, 0f, 0f))
                                                .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> moveTo(ctx, yaw(ctx), 0f))
                                                        .then(Commands.argument("pitch", DoubleArgumentType.doubleArg())
                                                                .executes(
                                                                        ctx -> moveTo(ctx, yaw(ctx), pitch(ctx)))))))));
    }

    private int moveTo(CommandContext<CommandSourceStack> ctx, float yaw, float pitch) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        double x = ctx.getArgument("x", Double.class);
        double y = ctx.getArgument("y", Double.class);
        double z = ctx.getArgument("z", Double.class);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_COORDS, Map.of("coords", x + " " + y + " " + z));
            return 0;
        }
        services.moveTo().moveTo(ref(sender), nameArg(ctx), x, y, z, yaw, pitch);
        return Command.SINGLE_SUCCESS;
    }

    private static float yaw(CommandContext<CommandSourceStack> ctx) {
        return (float) (double) ctx.getArgument("yaw", Double.class);
    }

    private static float pitch(CommandContext<CommandSourceStack> ctx) {
        return (float) (double) ctx.getArgument("pitch", Double.class);
    }

    private LiteralArgumentBuilder<CommandSourceStack> displayNameNode() {
        return Commands.literal("displayname")
                .then(nameArgument()
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::displayName)));
    }

    private int displayName(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String text = ctx.getArgument("text", String.class).strip();
        String resolved = text.equalsIgnoreCase("none") || text.isBlank() ? null : text;
        services.displayName().setDisplayName(ref(sender), nameArg(ctx), resolved);
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> cooldownNode() {
        return Commands.literal("cooldown")
                .then(nameArgument()
                        .then(Commands.argument("millis", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        suggest(builder, List.of(DEFAULT_KEYWORD, "0", "500", "1000")))
                                .executes(this::cooldown)));
    }

    private int cooldown(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String word = ctx.getArgument("millis", String.class).strip();
        Long millis = word.equalsIgnoreCase(DEFAULT_KEYWORD) ? 0L : parseNonNegativeLong(word);
        if (millis == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_COOLDOWN, Map.of("cooldown", word));
            return 0;
        }
        services.cooldown().setCooldown(ref(sender), nameArg(ctx), millis);
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> distanceNode(String literal, SetNpcRange.Kind kind) {
        return Commands.literal(literal)
                .then(nameArgument()
                        .then(Commands.argument("blocks", StringArgumentType.word())
                                .suggests(
                                        (ctx, builder) -> suggest(builder, List.of(DEFAULT_KEYWORD, "16", "48", "64")))
                                .executes(ctx -> distance(ctx, kind))));
    }

    private int distance(CommandContext<CommandSourceStack> ctx, SetNpcRange.Kind kind) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String word = ctx.getArgument("blocks", String.class).strip();
        if (word.equalsIgnoreCase(DEFAULT_KEYWORD)) {
            services.range().setRange(ref(sender), nameArg(ctx), kind, null);
            return Command.SINGLE_SUCCESS;
        }
        Double blocks = parseNonNegativeDouble(word);
        if (blocks == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_DISTANCE, Map.of("distance", word));
            return 0;
        }
        services.range().setRange(ref(sender), nameArg(ctx), kind, blocks);
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> stateNode() {
        return Commands.literal("state")
                .then(nameArgument()
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggest(builder, STATE_WORDS))
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .suggests((ctx, builder) -> suggest(builder, BOOLEAN_WORDS))
                                        .executes(this::state))));
    }

    private int state(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String word = ctx.getArgument("state", String.class).strip().toUpperCase(Locale.ROOT);
        SetNpcState.Flag flag = parseFlag(word);
        if (flag == null) {
            feedback.send(
                    sender, NpcMessageKey.NPC_INVALID_STATE, Map.of("state", ctx.getArgument("state", String.class)));
            return 0;
        }
        services.state().setState(ref(sender), nameArg(ctx), flag, ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    private int mirror(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.mirror().setMirror(ref(sender), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    private int collidable(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.collidable().setCollidable(ref(sender), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    private int showInTab(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.showInTab().setShowInTab(ref(sender), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    private int skinSlim(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.skinSlim().setSlim(ref(sender), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    /** Parse a state word to a {@link SetNpcState.Flag}, or {@code null} when it names no known state. */
    private static SetNpcState.@org.jspecify.annotations.Nullable Flag parseFlag(String upper) {
        try {
            return SetNpcState.Flag.valueOf(upper);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Parse a non-negative long, or {@code null} when the word is not a non-negative integer. */
    private static @org.jspecify.annotations.Nullable Long parseNonNegativeLong(String word) {
        try {
            long value = Long.parseLong(word);
            return value < 0 ? null : value;
        } catch (NumberFormatException notALong) {
            return null;
        }
    }

    /** Parse a finite, non-negative double, or {@code null} when the word is not one. */
    private static @org.jspecify.annotations.Nullable Double parseNonNegativeDouble(String word) {
        try {
            double value = Double.parseDouble(word);
            return Double.isFinite(value) && value >= 0.0 ? value : null;
        } catch (NumberFormatException notADouble) {
            return null;
        }
    }
}
