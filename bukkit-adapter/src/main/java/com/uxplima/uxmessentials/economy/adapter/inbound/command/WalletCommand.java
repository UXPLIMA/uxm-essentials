package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wallet [currency]} (aliases {@code /cüzdan}): opens the visual wallet GUI dashboard.
 */
@NullMarked
public final class WalletCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.wallet";

    public WalletCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("wallet")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runOwn)
                .then(currencyArgument().executes(this::runOwn))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("cüzdan");
    }

    @Override
    public String description() {
        return "Open your visual wallet dashboard.";
    }

    private int runOwn(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }
        services.walletView().open(sender, currency.get());
        return Command.SINGLE_SUCCESS;
    }
}
