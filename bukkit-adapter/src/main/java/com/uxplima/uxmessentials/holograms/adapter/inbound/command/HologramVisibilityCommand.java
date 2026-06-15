package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The FancyHolograms-style per-player visibility subcommands under {@code /hologram}: {@code visibility} (who
 * may see the hologram — {@code ALL} for everyone or {@code PERMISSION} gated by an operator-chosen node) and
 * {@code visibilitydistance} (the visibility radius in blocks, 0 = unlimited). Each maps its parsed argument to
 * one {@link Visibility} transition on the shared {@link HologramServices}; collected here, alongside
 * {@link HologramAppearanceCommand}, so the main {@code /hologram} command class stays focused on its
 * create/edit/move forms while keeping the single literal intact. The mode is validated and the distance clamped
 * here so an invalid value is rejected at the boundary, never inside a use case.
 *
 * <p>The permission node a {@code PERMISSION} hologram requires is the operator's own choice, so it is a dynamic
 * node — not a fixed plugin-declared one. The {@code /hologram} command as a whole stays gated by
 * {@code uxmessentials.hologram.use}; this only sets which node a viewer must hold to see one hologram.
 */
@NullMarked
final class HologramVisibilityCommand extends HologramCommandSupport {

    HologramVisibilityCommand(HologramServices services, Messages messages) {
        super(services, messages);
    }

    /** The visibility subcommand nodes the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(visibilityNode(), visibilityDistanceNode());
    }

    private LiteralArgumentBuilder<CommandSourceStack> visibilityNode() {
        return Commands.literal("visibility")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .executes(this::visibility)
                                .then(Commands.argument("permission", StringArgumentType.word())
                                        .executes(this::visibility))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> visibilityDistanceNode() {
        return Commands.literal("visibilitydistance")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0))
                                .executes(this::visibilityDistance)));
    }

    private int visibility(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Visibility.Mode> mode = parseMode(ctx.getArgument("mode", String.class));
        if (mode.isEmpty()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_VISIBILITY_MODE_INVALID, Map.of());
            return 0;
        }
        if (mode.orElseThrow() == Visibility.Mode.ALL) {
            services.visibility().setMode(ref(sender), nameArg(ctx), Visibility::toEveryone);
            return Command.SINGLE_SUCCESS;
        }
        Optional<String> node = optionalArgument(ctx, "permission");
        if (node.isEmpty()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_VISIBILITY_NEEDS_NODE, Map.of());
            return 0;
        }
        services.visibility().setMode(ref(sender), nameArg(ctx), current -> current.toPermission(node.orElseThrow()));
        return Command.SINGLE_SUCCESS;
    }

    private int visibilityDistance(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        int blocks = ctx.getArgument("blocks", Integer.class);
        services.visibility().setDistance(ref(sender), nameArg(ctx), blocks);
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<Visibility.Mode> parseMode(String raw) {
        for (Visibility.Mode mode : Visibility.Mode.values()) {
            if (mode.name().equalsIgnoreCase(raw)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> optionalArgument(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return Optional.of(ctx.getArgument(name, String.class));
        } catch (IllegalArgumentException absent) {
            // Brigadier throws when an optional argument node was not supplied on this branch; treat as absent.
            return Optional.empty();
        }
    }

    private static HologramName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HologramName.of(ctx.getArgument("name", String.class));
    }
}
