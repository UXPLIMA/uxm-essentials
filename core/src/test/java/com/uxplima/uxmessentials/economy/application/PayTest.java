package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.fakes.CapturingSink;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryPayPreferences;
import com.uxplima.uxmessentials.economy.fakes.InMemoryPendingPayRegistry;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.economy.fakes.KeyMessages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code /pay} and {@code /payconfirm} use case through the native provider and the real gates: a
 * transfer below the confirm-threshold debits the payer and credits the target atomically, paying yourself
 * is denied, an amount below {@code min-pay} is rejected before any leg, a target with {@code /paytoggle}
 * off refuses the transfer before the debit, and a transfer above the confirm-threshold is staged (no debit)
 * until {@code /payconfirm} re-validates and applies it.
 */
class PayTest {

    private InMemoryWalletRepository repo;
    private InMemoryPayPreferences preferences;
    private InMemoryPendingPayRegistry pending;
    private CapturingSink sink;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        repo = new InMemoryWalletRepository();
        preferences = new InMemoryPayPreferences();
        pending = new InMemoryPendingPayRegistry();
        sink = new CapturingSink();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    private Pay payWith(java.util.Set<com.uxplima.uxmessentials.economy.domain.Currency> currencies) {
        com.uxplima.uxmessentials.economy.domain.Currency def =
                currencies.iterator().next();
        CurrencyRegistry registry = CurrencyRegistry.of(currencies, def.id());
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        NativeEconomyProvider provider = new NativeEconomyProvider(repo, registry, clock);
        EconomyNotifier notifier = new EconomyNotifier(new KeyMessages(), sink);
        return new Pay(provider, preferences, pending, notifier, clock);
    }

    @Test
    void payBelowThresholdMovesBalanceAtomically() {
        Pay pay = payWith(java.util.Set.of(Currencies.COINS));
        repo.credit(alice, Money.of(Currencies.COINS, 100));

        PayOutcome outcome = pay.pay(alice, bob, Money.of(Currencies.COINS, 40));

        assertThat(outcome.kind()).isEqualTo(PayOutcome.Kind.SENT);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 60));
        assertThat(repo.findByOwner(bob).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 40));
    }

    @Test
    void payingYourselfIsRejected() {
        Pay pay = payWith(java.util.Set.of(Currencies.COINS));
        repo.credit(alice, Money.of(Currencies.COINS, 100));

        PayOutcome outcome = pay.pay(alice, alice, Money.of(Currencies.COINS, 10));

        assertThat(outcome.error()).get().isEqualTo(TransferError.SELF_TRANSFER);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 100));
    }

    @Test
    void payBelowMinimumIsRejected() {
        com.uxplima.uxmessentials.economy.domain.Currency minted =
                com.uxplima.uxmessentials.economy.domain.Currency.builder(
                                com.uxplima.uxmessentials.economy.domain.CurrencyId.of("coins"))
                        .minPay(java.math.BigDecimal.TEN)
                        .build();
        Pay pay = payWith(java.util.Set.of(minted));
        repo.credit(alice, Money.of(minted, 100));

        PayOutcome outcome = pay.pay(alice, bob, Money.of(minted, 5));

        assertThat(outcome.error()).get().isEqualTo(TransferError.BELOW_MINIMUM);
    }

    @Test
    void transferDisabledCurrencyIsRejectedBeforeDebit() {
        com.uxplima.uxmessentials.economy.domain.Currency gems =
                com.uxplima.uxmessentials.economy.domain.Currency.builder(
                                com.uxplima.uxmessentials.economy.domain.CurrencyId.of("gems"))
                        .transferAllowed(false)
                        .build();
        Pay pay = payWith(java.util.Set.of(gems));
        repo.credit(alice, Money.of(gems, 100));

        PayOutcome outcome = pay.pay(alice, bob, Money.of(gems, 40));

        assertThat(outcome.error()).get().isEqualTo(TransferError.CURRENCY_TRANSFER_DISABLED);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(gems)).isEqualTo(Money.of(gems, 100));
    }

    @Test
    void targetWithPayDisabledRefusesBeforeDebit() {
        Pay pay = payWith(java.util.Set.of(Currencies.COINS));
        repo.credit(alice, Money.of(Currencies.COINS, 100));
        preferences.set(bob, false);

        PayOutcome outcome = pay.pay(alice, bob, Money.of(Currencies.COINS, 40));

        assertThat(outcome.error()).get().isEqualTo(TransferError.TARGET_DISABLED);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 100));
    }

    @Test
    void largePayIsStagedThenAppliedOnConfirm() {
        Pay pay = payWith(java.util.Set.of(Currencies.confirmCoins(50)));
        com.uxplima.uxmessentials.economy.domain.Currency currency = Currencies.confirmCoins(50);
        repo.credit(alice, Money.of(currency, 200));

        PayOutcome staged = pay.pay(alice, bob, Money.of(currency, 100));
        assertThat(staged.kind()).isEqualTo(PayOutcome.Kind.STAGED);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(currency))
                .isEqualTo(Money.of(currency, 200)); // no debit while staged

        PayOutcome confirmed = pay.confirm(alice).orElseThrow();
        assertThat(confirmed.kind()).isEqualTo(PayOutcome.Kind.SENT);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(currency)).isEqualTo(Money.of(currency, 100));
        assertThat(repo.findByOwner(bob).orElseThrow().balanceOf(currency)).isEqualTo(Money.of(currency, 100));
    }

    @Test
    void confirmWithNothingStagedIsEmpty() {
        Pay pay = payWith(java.util.Set.of(Currencies.COINS));

        assertThat(pay.confirm(alice)).isEmpty();
        assertThat(sink.delivered("pay.confirm-none")).isTrue();
    }

    @Test
    void payToOfflineNeverJoinedTargetCreatesTheirRow() {
        Pay pay = payWith(java.util.Set.of(Currencies.COINS));
        repo.credit(alice, Money.of(Currencies.COINS, 100));
        PlayerRef neverJoined = new PlayerRef(UUID.randomUUID(), "Ghost");

        PayOutcome outcome = pay.pay(alice, neverJoined, Money.of(Currencies.COINS, 25));

        assertThat(outcome.kind()).isEqualTo(PayOutcome.Kind.SENT);
        assertThat(repo.findByOwner(neverJoined).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 25));
    }
}
