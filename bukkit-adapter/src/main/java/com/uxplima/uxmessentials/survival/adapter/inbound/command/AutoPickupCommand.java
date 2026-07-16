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
 * {@code /autopickup}: flip the caller's personal auto-pickup toggle, gated by
 * {@code uxmessentials.survival.autopickup.toggle}. The toggle is PDC-backed and defaults to on (the mechanic ships
 * enabled), so a player who has never run the command has auto-pickup active; running it once turns it off, again on.
 */
@NullMarked
public final class AutoPickupCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.survival.autopickup.toggle";

    private final PdcSurvivalToggles toggles;
    private final CommandFeedback feedback;

    public AutoPickupCommand(PdcSurvivalToggles toggles, Messages messages) {
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("autopickup")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .build();
    }

    @Override
    public String description() {
        return "Toggle auto-pickup: mined drops go straight to your inventory.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        boolean nowActive = toggles.toggleAutoPickup(player, true);
        feedback.send(
                player,
                nowActive
                        ? SurvivalMessageKey.SURVIVAL_AUTOPICKUP_ENABLED
                        : SurvivalMessageKey.SURVIVAL_AUTOPICKUP_DISABLED);
        return Command.SINGLE_SUCCESS;
    }
}
