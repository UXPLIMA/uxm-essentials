package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.economy.application.port.PendingPayRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.PendingPay;
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.Test;

class PayTest {

    private static final Currency DOLLARS = Currency.builder(CurrencyId.of("dollars"))
            .minPay(BigDecimal.ONE)
            .confirmThreshold(new BigDecimal("1000"))
            .build();
    private static final Currency TOKENS =
            Currency.builder(CurrencyId.of("tokens")).transferAllowed(false).build();

    private final Bank bank = new Bank(DOLLARS, TOKENS);
    private final Set<PlayerRef> payOff = new HashSet<>();
    private final Staged staged = new Staged();
    private final List<String> lines = new ArrayList<>();
    private final EconomyNotifier notifier = new EconomyNotifier(
            (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                lines.add(viewer.name() + ":" + key.key() + placeholders.getOrDefault("amount", ""));
                return key.key();
            },
            (viewer, text) -> {});
    private final List<Money> taxSink = new ArrayList<>();

    private Pay pay(PayTaxation taxation) {
        PayPreferences preferences = new PayPreferences() {
            @Override
            public boolean acceptsPay(PlayerRef target) {
                return !payOff.contains(target);
            }

            @Override
            public boolean toggle(PlayerRef who) {
                return payOff.add(who) || !payOff.remove(who);
            }
        };
        return new Pay(bank, preferences, staged, notifier, taxation, CLOCK);
    }

    private Pay pay() {
        return pay(PayTaxation.none());
    }

    private static Money dollars(String amount) {
        return Money.of(DOLLARS, new BigDecimal(amount));
    }

    @Test
    void aPaymentMovesTheMoneyAndTellsBothSides() {
        bank.set(ALEX, dollars("100"));

        PayOutcome outcome = pay().pay(ALEX, SAM, dollars("40"));

        assertThat(outcome).isEqualTo(PayOutcome.sent());
        assertThat(bank.balance(ALEX, DOLLARS).amount()).isEqualByComparingTo("60");
        assertThat(bank.balance(SAM, DOLLARS).amount()).isEqualByComparingTo("40");
        assertThat(lines).containsExactly("Alex:pay.sent$40.00", "Sam:pay.received$40.00");
    }

    @Test
    void aPlayerCannotPayThemself() {
        bank.set(ALEX, dollars("100"));

        assertThat(pay().pay(ALEX, ALEX, dollars("5")).error()).contains(TransferError.SELF_TRANSFER);
        assertThat(bank.balance(ALEX, DOLLARS).amount()).isEqualByComparingTo("100");
    }

    @Test
    void theRulesOfTheCurrencyAndTheReceiverAreChecked() {
        bank.set(ALEX, dollars("100"));
        payOff.add(SAM);
        Pay pay = pay();

        assertThat(pay.pay(ALEX, SAM, dollars("5")).error()).contains(TransferError.TARGET_DISABLED);
        payOff.clear();
        assertThat(pay.pay(ALEX, SAM, dollars("0.50")).error()).contains(TransferError.BELOW_MINIMUM);
        assertThat(pay.pay(ALEX, SAM, Money.of(TOKENS, 5)).error()).contains(TransferError.CURRENCY_TRANSFER_DISABLED);
        assertThat(bank.balance(SAM, DOLLARS).isZero()).isTrue();
    }

    @Test
    void aPaymentLargerThanTheBalanceIsRefused() {
        bank.set(ALEX, dollars("10"));

        assertThat(pay().pay(ALEX, SAM, dollars("11")).error()).contains(TransferError.INSUFFICIENT_FUNDS);
        assertThat(bank.balance(ALEX, DOLLARS).amount()).isEqualByComparingTo("10");
    }

    @Test
    void aLargePaymentWaitsForConfirmation() {
        bank.set(ALEX, dollars("5000"));
        Pay pay = pay();

        assertThat(pay.pay(ALEX, SAM, dollars("2000"))).isEqualTo(PayOutcome.staged());
        assertThat(bank.balance(SAM, DOLLARS).isZero()).isTrue();

        assertThat(pay.confirm(ALEX)).contains(PayOutcome.sent());
        assertThat(bank.balance(SAM, DOLLARS).amount()).isEqualByComparingTo("2000");
    }

    @Test
    void aConfirmedPaymentIsCheckedAgain() {
        bank.set(ALEX, dollars("5000"));
        Pay pay = pay();
        pay.pay(ALEX, SAM, dollars("2000"));
        payOff.add(SAM);

        assertThat(pay.confirm(ALEX))
                .hasValueSatisfying(outcome -> assertThat(outcome.error()).contains(TransferError.TARGET_DISABLED));
        assertThat(bank.balance(ALEX, DOLLARS).amount()).isEqualByComparingTo("5000");
    }

    @Test
    void confirmingWithNothingStagedSaysSo() {
        assertThat(pay().confirm(ALEX)).isEmpty();
        assertThat(lines).containsExactly("Alex:pay.confirm-none");
    }

    @Test
    void theReceiverIsToldTheAmountAfterTax() {
        bank.set(ALEX, dollars("100"));
        PayTaxation taxed = new PayTaxation(
                new TaxPolicy(true, new BigDecimal("10"), BigDecimal.ONE),
                (receiver, tax) -> taxSink.add(tax),
                p -> false);

        pay(taxed).pay(ALEX, SAM, dollars("50"));

        assertThat(taxSink)
                .singleElement()
                .satisfies(tax -> assertThat(tax.amount()).isEqualByComparingTo("6"));
        assertThat(lines).containsExactly("Alex:pay.sent$50.00", "Alex:pay.tax-applied$6.00", "Sam:pay.received$44.00");
    }

    @Test
    void anExemptPayerPaysNoTax() {
        bank.set(ALEX, dollars("100"));
        PayTaxation taxed = new PayTaxation(
                new TaxPolicy(true, new BigDecimal("10"), BigDecimal.ZERO),
                (receiver, tax) -> taxSink.add(tax),
                p -> true);

        pay(taxed).pay(ALEX, SAM, dollars("50"));

        assertThat(taxSink).isEmpty();
        assertThat(lines).contains("Sam:pay.received$50.00");
    }

    @Test
    void taxNeverExceedsThePayment() {
        TaxPolicy steep = new TaxPolicy(true, new BigDecimal("50"), new BigDecimal("100"));

        assertThat(steep.tax(dollars("20")).amount()).isEqualByComparingTo("20");
        assertThat(TaxPolicy.disabled().tax(dollars("20")).isZero()).isTrue();
    }

    /** Balances per player and currency, transferring only when the payer can cover it. */
    private static final class Bank implements EconomyProvider {

        private final Set<Currency> currencies;
        private final Map<String, Money> balances = new HashMap<>();

        private Bank(Currency... currencies) {
            this.currencies = Set.of(currencies);
        }

        void set(PlayerRef owner, Money amount) {
            balances.put(owner.uuid() + amount.currency().id().value(), amount);
        }

        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return true;
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {}

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return balances.getOrDefault(owner.uuid() + currency.id().value(), Money.zero(currency));
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            set(owner, balance(owner, amount.currency()).plus(amount));
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            Money have = balance(owner, amount.currency());
            if (have.isLessThan(amount)) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            set(owner, have.minus(amount));
            return Result.ok();
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            Money have = balance(from, amount.currency());
            if (have.isLessThan(amount)) {
                return TransferResult.insufficientFunds(amount, have);
            }
            debit(from, amount);
            credit(to, amount);
            return TransferResult.allow(
                    Transaction.debit(from, amount, balance(from, amount.currency()), NOW),
                    Transaction.credit(to, amount, balance(to, amount.currency()), NOW));
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public Set<Currency> currencies() {
            return currencies;
        }
    }

    /** One staged payment per payer. */
    private static final class Staged implements PendingPayRegistry {

        private final Map<PlayerRef, PendingPay> byPayer = new HashMap<>();

        @Override
        public void stage(PendingPay pending) {
            byPayer.put(pending.payer(), pending);
        }

        @Override
        public Optional<PendingPay> peek(PlayerRef payer) {
            return Optional.ofNullable(byPayer.get(payer));
        }

        @Override
        public Optional<PendingPay> take(PlayerRef payer) {
            return Optional.ofNullable(byPayer.remove(payer));
        }

        @Override
        public void clear(PlayerRef payer) {
            byPayer.remove(payer);
        }
    }
}
