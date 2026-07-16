package com.uxplima.uxmessentials.survival.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.SurvivalMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /veinminer}: flip the caller's personal veinminer toggle, gated by {@code uxmessentials.survival.veinminer.toggle}.
 * The toggle is PDC-backed and defaults to on (the mechanic ships enabled), so a player who has never run the command
 * has veinminer active; running it once turns it off, again on. The new state is echoed through
 * {@link SurvivalMessageKey}.
 */
@NullMarked
public final class VeinminerCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.survival.veinminer.toggle";

    private final PdcSurvivalToggles toggles;
    private final CommandFeedback feedback;

    public VeinminerCommand(PdcSurvivalToggles toggles, Messages messages) {
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("veinminer")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .build();
    }

    @Override
    public String description() {
        return "Toggle veinminer: break a whole ore vein by mining one block.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        boolean nowActive = toggles.toggleVeinminer(player, true);
        feedback.send(
                player,
                nowActive
                        ? SurvivalMessageKey.SURVIVAL_VEINMINER_ENABLED
                        : SurvivalMessageKey.SURVIVAL_VEINMINER_DISABLED);
        return Command.SINGLE_SUCCESS;
    }
}
