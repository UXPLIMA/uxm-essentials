package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.LoanService;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.LoanError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.NullMarked;

/**
 * The request-a-new-loan flow split out of {@link LoanDashboardMenu}: pick a currency through the shared engine
 * {@link CurrencyPickerMenu}, then prompt for an amount and an installment count through the shared input seam, then
 * submit the loan. Every label resolves through a {@code MessageKey} in the viewer's locale. The {@code refresh}
 * callback reopens the dashboard once the flow finishes or aborts.
 */
@NullMarked
final class LoanRequestFlow {

    private final LoanService loanService;
    private final CurrencyRegistry currencies;
    private final TextInput textInput;
    private final Scheduler scheduler;
    private final EconomyNotifier notifier;
    private final CurrencyPickerMenu picker;
    private final Consumer<Player> refresh;

    LoanRequestFlow(
            LoanService loanService,
            CurrencyRegistry currencies,
            TextInput textInput,
            Scheduler scheduler,
            EconomyNotifier notifier,
            CurrencyPickerMenu picker,
            Consumer<Player> refresh) {
        this.loanService = Objects.requireNonNull(loanService, "loanService");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.picker = Objects.requireNonNull(picker, "picker");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
    }

    /**
     * Begin the request flow: with no configured currency the request is rejected and the dashboard reopens; with a
     * single currency the amount prompt opens directly; otherwise the shared engine currency picker opens and the
     * pick continues the flow with the chosen currency.
     */
    void start(Player player) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        List<Currency> all = List.copyOf(currencies.all());
        if (all.isEmpty()) {
            notifier.send(viewerRef, EconomyMessageKey.LOAN_GUI_NO_CURRENCIES);
            refresh.accept(player);
            return;
        }
        if (all.size() == 1) {
            promptLoanAmount(player, all.get(0));
            return;
        }
        picker.open(player, viewerRef, all, all.get(0), chosen -> promptLoanAmount(player, chosen));
    }

    private void promptLoanAmount(Player player, Currency currency) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        textInput.prompt(
                player,
                viewerRef,
                InputRequest.of(
                        "loan.amount",
                        EconomyMessageKey.LOAN_GUI_AMOUNT_PROMPT,
                        Map.of("currency", currency.id().value())),
                amountStr -> applyAmount(player, viewerRef, currency, amountStr),
                () -> refresh.accept(player));
    }

    /** Parse the typed amount against {@code currency}; on success continue to the installment prompt. Package-private for tests. */
    void applyAmount(Player player, PlayerRef viewerRef, Currency currency, String amountStr) {
        Result<Money, AmountParseError> parsed = AmountParser.parse(amountStr, currency);
        if (parsed.isErr()) {
            notifier.send(viewerRef, EconomyMessageKey.LOAN_GUI_INVALID_AMOUNT);
            refresh.accept(player);
            return;
        }
        promptInstallments(player, viewerRef, currency, parsed.orElseThrow());
    }

    private void promptInstallments(Player player, PlayerRef viewerRef, Currency currency, Money amount) {
        textInput.prompt(
                player,
                viewerRef,
                InputRequest.of("loan.installments", EconomyMessageKey.LOAN_GUI_INSTALLMENTS_PROMPT),
                installmentsStr -> applyInstallments(player, viewerRef, currency, amount, installmentsStr),
                () -> refresh.accept(player));
    }

    /** Parse and range-check the installment count; on success submit the loan. Package-private for tests. */
    void applyInstallments(
            Player player, PlayerRef viewerRef, Currency currency, Money amount, String installmentsStr) {
        int installments;
        try {
            installments = Integer.parseInt(installmentsStr.trim());
        } catch (NumberFormatException malformed) {
            notifier.send(viewerRef, EconomyMessageKey.LOAN_GUI_INSTALLMENTS_INVALID);
            refresh.accept(player);
            return;
        }
        if (installments < 1 || installments > 100) {
            notifier.send(viewerRef, EconomyMessageKey.LOAN_GUI_INSTALLMENTS_RANGE);
            refresh.accept(player);
            return;
        }
        submitLoan(player, viewerRef, currency, amount, installments);
    }

    private void submitLoan(Player player, PlayerRef viewerRef, Currency currency, Money amount, int installments) {
        scheduler.async(() -> {
            Result<Loan, LoanError> res = loanService.takeLoan(viewerRef, amount, installments);
            scheduler.onEntity(viewerRef, () -> {
                if (res.isOk()) {
                    announceApproval(viewerRef, currency, amount, res.orElseThrow());
                } else {
                    notifier.send(viewerRef, EconomyMessageKey.LOAN_GUI_REJECTED);
                }
                refresh.accept(player);
            });
        });
    }

    private void announceApproval(PlayerRef viewerRef, Currency currency, Money amount, Loan loan) {
        notifier.send(
                viewerRef,
                EconomyMessageKey.LOAN_GUI_APPROVED,
                Map.of(
                        "amount", amount.amount().toPlainString(),
                        "currency", currency.id().value()));
        notifier.send(
                viewerRef,
                EconomyMessageKey.LOAN_GUI_APPROVED_DETAIL,
                Map.of(
                        "amount", loan.remainingAmount().amount().toPlainString(),
                        "currency", currency.id().value(),
                        "installments", Integer.toString(loan.remainingInstallments())));
    }
}
