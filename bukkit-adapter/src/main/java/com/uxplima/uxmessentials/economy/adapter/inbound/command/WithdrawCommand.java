package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Handles `/withdraw <amount> [currency]` to convert virtual money to physical banknote item.
 */
@NullMarked
public final class WithdrawCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.withdraw";

    private final BanknoteMinter minter;

    public WithdrawCommand(Plugin plugin, EconomyServices services, Messages messages) {
        super(services, messages);
        this.minter = new BanknoteMinter(plugin, messages, services.banknoteStore());
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("withdraw")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> usage(ctx, "withdraw", "<amount> [currency]", "Withdraw money into a banknote"))
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(this::run)
                        .then(currencyArgument().executes(this::run)))
                .build();
    }

    @Override
    public String description() {
        return "Withdraw virtual money into a physical banknote item.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }
        if (!currencyPermitted(sender, currency.get())) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Money> money = amount(ctx.getArgument("amount", String.class), currency.get(), ref(sender));
        if (money.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        offTick(() -> withdraw(sender, ref(sender), money.get()));
        return Command.SINGLE_SUCCESS;
    }

    void withdraw(Player player, PlayerRef owner, Money amount) {
        if (amount.currency().isPhysical()) {
            services.notifier().send(owner, EconomyMessageKey.WITHDRAW_PHYSICAL_NOT_ALLOWED);
            return;
        }

        if (amount.amount().signum() <= 0) {
            services.notifier().send(owner, EconomyMessageKey.WITHDRAW_INVALID_AMOUNT);
            return;
        }

        Result<Unit, ?> result = services.provider().debit(owner, amount);
        if (result.isErr()) {
            services.notifier()
                    .send(
                            owner,
                            EconomyMessageKey.WITHDRAW_INSUFFICIENT,
                            Map.of("amount", services.notifier().amount(amount)));
            return;
        }

        ItemStack banknote = minter.mint(owner, amount);
        services.scheduler().onEntity(owner, () -> {
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(banknote);
            if (!remaining.isEmpty()) {
                for (ItemStack left : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }

            services.notifier()
                    .send(
                            owner,
                            EconomyMessageKey.BANKNOTE_WITHDRAWN,
                            Map.of("amount", services.notifier().amount(amount)));
        });
    }
}
