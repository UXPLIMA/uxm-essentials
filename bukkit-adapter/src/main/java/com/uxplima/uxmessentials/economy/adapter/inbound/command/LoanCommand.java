package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.LoanError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Handles `/loan` commands for credit rating scoring and loans.
 */
@NullMarked
public final class LoanCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.loan";

    public LoanCommand(Plugin plugin, EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("loan")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runStatus)
                .then(Commands.literal("status").executes(this::runStatus))
                .then(Commands.literal("take")
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(ctx -> this.runTake(ctx, null, 10)) // default 10 installments
                                .then(Commands.argument("installments", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> this.runTake(
                                                ctx, null, ctx.getArgument("installments", Integer.class))))
                                .then(currencyArgument()
                                        .executes(
                                                ctx -> this.runTake(ctx, ctx.getArgument("currency", String.class), 10))
                                        .then(Commands.argument("installments", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> this.runTake(
                                                        ctx,
                                                        ctx.getArgument("currency", String.class),
                                                        ctx.getArgument("installments", Integer.class)))))))
                .then(Commands.literal("pay")
                        .then(Commands.argument("loan_id", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services.loanService()::getActiveLoanIds))
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(this::runPay))))
                .build();
    }

    @Override
    public String description() {
        return "Debtor loans and credit rating.";
    }

    private int runStatus(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) return 0;

        services.loanGuiView().open(sender);
        return Command.SINGLE_SUCCESS;
    }

    private int runTake(CommandContext<CommandSourceStack> ctx, @Nullable String currencyId, int installments) {
        Player sender = player(ctx);
        if (sender == null) return 0;

        Currency currency;
        if (currencyId == null) {
            currency = services.currencies().defaultCurrency();
        } else {
            Optional<Currency> opt = services.currencies().find(CurrencyId.of(currencyId));
            if (opt.isEmpty()) {
                rejectUnknownCurrency(ref(sender));
                return Command.SINGLE_SUCCESS;
            }
            currency = opt.get();
        }

        PlayerRef senderRef = ref(sender);
        Optional<Money> money = amount(ctx.getArgument("amount", String.class), currency, senderRef);
        if (money.isEmpty()) return Command.SINGLE_SUCCESS;

        offTick(() -> {
            Result<Loan, LoanError> res = services.loanService().takeLoan(senderRef, money.get(), installments);
            services.scheduler().onEntity(senderRef, () -> {
                if (res.isOk()) {
                    Loan loan = res.orElseThrow();
                    services.notifier()
                            .send(
                                    senderRef,
                                    com.uxplima.uxmessentials.economy.application.EconomyMessageKey.LOAN_APPROVED,
                                    java.util.Map.of(
                                            "amount",
                                            services.notifier().amount(loan.remainingAmount()),
                                            "installments",
                                            Integer.toString(loan.remainingInstallments())));
                } else {
                    services.notifier().send(senderRef, res.errorOrThrow().messageKey());
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runPay(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) return 0;

        String loanId = ctx.getArgument("loan_id", String.class);
        PlayerRef senderRef = ref(sender);

        offTick(() -> {
            Optional<Loan> oLoan = services.loanService().getActiveLoans(senderRef).stream()
                    .filter(l -> l.id().equalsIgnoreCase(loanId))
                    .findFirst();

            if (oLoan.isEmpty()) {
                services.scheduler().onEntity(senderRef, () -> services.notifier()
                        .send(
                                senderRef,
                                com.uxplima.uxmessentials.economy.application.EconomyMessageKey.LOAN_NOT_FOUND));
                return;
            }

            Loan loan = oLoan.get();
            Currency currency = loan.remainingAmount().currency();

            services.scheduler().onEntity(senderRef, () -> {
                Optional<Money> money = amount(ctx.getArgument("amount", String.class), currency, senderRef);
                if (money.isEmpty()) return;

                offTick(() -> {
                    Result<Unit, LoanError> res = services.loanService().payInstallment(senderRef, loanId, money.get());
                    services.scheduler().onEntity(senderRef, () -> {
                        if (res.isOk()) {
                            services.notifier()
                                    .send(
                                            senderRef,
                                            com.uxplima.uxmessentials.economy.application.EconomyMessageKey.LOAN_PAID,
                                            java.util.Map.of(
                                                    "remaining",
                                                    services.notifier()
                                                            .amount(loan.remainingAmount()
                                                                    .minus(money.get()))));
                        } else {
                            services.notifier()
                                    .send(senderRef, res.errorOrThrow().messageKey());
                        }
                    });
                });
            });
        });
        return Command.SINGLE_SUCCESS;
    }
}
