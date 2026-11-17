package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.economy.application.port.PendingPayRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.PendingPay;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.message.DeferredPlayerNotices;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.PlayerNotices;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.ManualScheduler;
import com.uxplima.uxmessentials.testing.OnlineLookup;
import com.uxplima.uxmessentials.testing.QuietLog;
import org.junit.jupiter.api.Test;

class PayTest {

    private static final Currency DOLLARS = Currency.builder(CurrencyId.of("dollars"))
            .minPay(BigDecimal.ONE)
            .confirmThreshold(new BigDecimal("1000"))
            .build();
    private static final Currency TOKENS =
            Currency.builder(CurrencyId.of("tokens")).transferAllowed(false).build();

    private final InMemoryBank bank = new InMemoryBank(DOLLARS, TOKENS);
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
        return pay(taxation, notifier::send);
    }

    private Pay pay(PayTaxation taxation, PlayerNotices payees) {
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
        return new Pay(bank, preferences, staged, notifier, payees, taxation, CLOCK);
    }

    private Pay pay() {
        return pay(PayTaxation.none());
    }

    private static Money dollars(String amount) {
        return Money.of(DOLLARS, new BigDecimal(amount));
    }

    @Test
    void aPayeeWhoCannotBeToldIsStillPaidAndPayAllCarriesOnToEveryone() {
        PlayerRef kim = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), "Kim");
        bank.set(ALEX, dollars("100"));
        QuietLog log = new QuietLog();
        // Nobody is online here and the store the line would wait in is down: the money has moved all the same.
        DeferredPlayerNotices unkeepable = new DeferredPlayerNotices(
                new DownStore(),
                new OnlineLookup(),
                new Notifier((viewer, key, args) -> key.key(), (viewer, text) -> {}),
                new ManualScheduler(),
                log,
                CLOCK,
                10);
        PayAll payAll = new PayAll(pay(PayTaxation.none(), unkeepable), notifier);

        payAll.payAll(ALEX, List.of(SAM, kim), dollars("10"));

        assertThat(bank.balance(SAM, DOLLARS).amount()).isEqualByComparingTo("10");
        assertThat(bank.balance(kim, DOLLARS).amount()).isEqualByComparingTo("10");
        assertThat(bank.balance(ALEX, DOLLARS).amount()).isEqualByComparingTo("80");
        assertThat(lines).contains("Alex:wallet.payall-sent$10.00");
        assertThat(log.errors()).hasSize(2);
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
    void aPaymentTheLedgerCouldNotCompleteIsAnsweredAndMovesNothing() {
        bank.set(ALEX, dollars("100"));
        bank.breakTransfers();

        assertThatThrownBy(() -> pay().pay(ALEX, SAM, dollars("40"))).hasMessage("connection lost");

        assertThat(lines).containsExactly("Alex:command.error");
        assertThat(bank.balance(ALEX, DOLLARS).amount()).isEqualByComparingTo("100");
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

    /** A kept-line store whose database is down. */
    private static final class DownStore implements PendingPlayerNotices {

        @Override
        public void keep(PlayerNotice notice) {
            throw new IllegalStateException("database unreachable");
        }

        @Override
        public List<PlayerNotice> take(UUID recipient) {
            throw new IllegalStateException("database unreachable");
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            throw new IllegalStateException("database unreachable");
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
