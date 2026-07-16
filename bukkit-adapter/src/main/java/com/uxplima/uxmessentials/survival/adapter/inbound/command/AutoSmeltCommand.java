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
 * {@code /autosmelt}: flip the caller's personal auto-smelt toggle, gated by
 * {@code uxmessentials.survival.autosmelt.toggle}. The toggle is PDC-backed and defaults to on (the mechanic ships
 * enabled), so a player who has never run the command has auto-smelt active; running it once turns it off, again on.
 */
@NullMarked
public final class AutoSmeltCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.survival.autosmelt.toggle";

    private final PdcSurvivalToggles toggles;
    private final CommandFeedback feedback;

    public AutoSmeltCommand(PdcSurvivalToggles toggles, Messages messages) {
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("autosmelt")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .build();
    }

    @Override
    public String description() {
        return "Toggle auto-smelt: ores are smelted as you mine them.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        boolean nowActive = toggles.toggleAutoSmelt(player, true);
        feedback.send(
                player,
                nowActive
                        ? SurvivalMessageKey.SURVIVAL_AUTOSMELT_ENABLED
                        : SurvivalMessageKey.SURVIVAL_AUTOSMELT_DISABLED);
        return Command.SINGLE_SUCCESS;
    }
}
