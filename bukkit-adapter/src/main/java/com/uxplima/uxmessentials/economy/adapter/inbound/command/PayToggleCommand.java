package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /paytoggle}: flip the player's persisted accept-pay flag and report its new state. When off, any
 * inbound {@code /pay} resolves {@code TransferError.TARGET_DISABLED} before the debit leg runs, so the payer
 * is never charged for a transfer the target refuses ({@code docs/11-economy-integration.md} §9.1). The flag
 * write is a queryable key-value row behind the {@code PayPreferences} port; the toggle runs off the tick
 * thread.
 */
@NullMarked
public final class PayToggleCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.pay.toggle";

    public PayToggleCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("paytoggle")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Toggle accepting incoming pay.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        offTick(() -> services.payToggle().toggle(ref(sender)));
        return Command.SINGLE_SUCCESS;
    }
}
