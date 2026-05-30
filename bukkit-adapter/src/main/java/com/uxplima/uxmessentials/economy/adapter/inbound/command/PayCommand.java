package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.math.BigDecimal;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pay <player> <amount> [currency]}: transfer funds to another player. The gates (self-pay,
 * {@code min-pay}, the target's {@code /paytoggle}, the per-currency confirm-threshold) and the atomic move
 * are the {@link com.uxplima.uxmessentials.economy.application.Pay} use case's job; this handler resolves the
 * target name and the currency at the boundary, parses the amount to the currency's precision, and runs the
 * provider call off the tick thread so a foreign provider can never wedge the command (§4.3).
 *
 * <p>The currency is resolved before any provider call (an unknown id is an error listing the valid ids).
 * Above the currency's confirm-threshold the use case stages the pay for {@code /payconfirm} — no money moves
 * — and below it the transfer is immediate.
 */
@NullMarked
public final class PayCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.pay";

    public PayCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pay")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(this::run)
                                .then(Commands.argument("currency", StringArgumentType.word())
                                        .executes(this::run))))
                .build();
    }

    @Override
    public String description() {
        return "Transfer funds to another player.";
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
        Optional<BigDecimal> amount = amount(ctx.getArgument("amount", String.class), currency.get());
        if (amount.isEmpty()) {
            services.notifier()
                    .send(
                            ref(sender),
                            com.uxplima.uxmessentials.economy.application.EconomyMessageKey.PAY_BELOW_MINIMUM,
                            java.util.Map.of("amount", ctx.getArgument("amount", String.class)));
            return Command.SINGLE_SUCCESS;
        }
        String targetName = ctx.getArgument("player", String.class);
        Money money = Money.of(currency.get(), amount.get());
        offTick(() -> resolveAndPay(ref(sender), targetName, money));
        return Command.SINGLE_SUCCESS;
    }

    private void resolveAndPay(PlayerRef from, String targetName, Money amount) {
        Optional<PlayerRef> target = services.players().findOnlineByName(targetName);
        if (target.isEmpty()) {
            rejectUnknownTarget(from, targetName);
            return;
        }
        services.pay().pay(from, target.get(), amount);
    }
}
