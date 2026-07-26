package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The NPC-link subcommands under {@code /hologram}: {@code linknpc <name> <npc>} links a
 * hologram to an NPC so it floats above and follows the NPC, and {@code unlinknpc <name>} clears the link so the
 * hologram anchors to its own stored location again. The hologram argument completes against the current hologram
 * names and the npc argument against the current NPC names, both read off warm in-memory sets per keystroke.
 * Collected here so the main {@code /hologram} command class stays focused while keeping the single literal intact.
 */
@NullMarked
final class HologramNpcCommand extends HologramCommandSupport {

    /** A side-effect-free, non-blocking source of the current NPC names for the {@code linknpc} tab-completion. */
    private final Supplier<? extends Collection<String>> npcNames;

    HologramNpcCommand(
            HologramServices services,
            Messages messages,
            Supplier<? extends Collection<String>> hologramNames,
            Supplier<? extends Collection<String>> npcNames) {
        super(services, messages, hologramNames);
        this.npcNames = Objects.requireNonNull(npcNames, "npcNames");
    }

    /** The NPC-link subcommand nodes the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(linkNode(), name("unlinknpc", this::unlink));
    }

    private LiteralArgumentBuilder<CommandSourceStack> linkNode() {
        return Commands.literal("linknpc")
                .executes(ctx -> usage(ctx, "hologram linknpc", "<hologram> <npc>", "Link a hologram to an NPC"))
                .then(nameArgument("name")
                        .executes(
                                ctx -> usage(ctx, "hologram linknpc", "<hologram> <npc>", "Link a hologram to an NPC"))
                        .then(Commands.argument("npc", StringArgumentType.string())
                                .suggests(CommandSuggestions.fromStrings(npcNames))
                                .executes(this::link)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> name(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .executes(ctx -> usage(ctx, "hologram " + literal, "<name>", "Manage hologram " + literal))
                .then(nameArgument("name").executes(action));
    }

    private int link(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String npc = ctx.getArgument("npc", String.class);
        services.linkNpc().link(ref(sender), nameArg(ctx), npc);
        return Command.SINGLE_SUCCESS;
    }

    private int unlink(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.unlinkNpc().unlink(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private static HologramName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HologramName.of(ctx.getArgument("name", String.class));
    }
}
