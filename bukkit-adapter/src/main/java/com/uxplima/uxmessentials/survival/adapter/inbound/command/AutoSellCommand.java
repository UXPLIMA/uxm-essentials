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
 * {@code /autosell}: flip the caller's personal auto-sell toggle, gated by
 * {@code uxmessentials.survival.autosell.toggle}. The toggle is PDC-backed and defaults to off (unlike auto-pickup and
 * auto-smelt, which default on), so a player opts in by running this once, turning it on; running it again turns it
 * back off. It only ever sells anything on a server that has the {@code autosell} mechanic enabled and an economy
 * wired.
 */
@NullMarked
public final class AutoSellCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.survival.autosell.toggle";

    private final PdcSurvivalToggles toggles;
    private final CommandFeedback feedback;

    public AutoSellCommand(PdcSurvivalToggles toggles, Messages messages) {
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("autosell")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .build();
    }

    @Override
    public String description() {
        return "Toggle auto-sell: priced drops are sold for coin as you mine.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        boolean nowActive = toggles.toggleAutoSell(player, false);
        feedback.send(
                player,
                nowActive
                        ? SurvivalMessageKey.SURVIVAL_AUTOSELL_ENABLED
                        : SurvivalMessageKey.SURVIVAL_AUTOSELL_DISABLED);
        return Command.SINGLE_SUCCESS;
    }
}
