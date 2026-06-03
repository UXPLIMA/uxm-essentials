package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
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
 * {@code /payall}: pay every online recipient from the sender's own wallet, delegating each leg to {@link Pay}
 * so the per-recipient gates and the atomic move are unchanged. The sender is skipped (never pays themselves)
 * and is debited once per successful recipient; the summary reports how many were paid.
 */
class PayAllTest {

    private InMemoryWalletRepository repo;
    private CapturingSink sink;
    private PlayerRef alice;
    private PlayerRef bob;
    private PlayerRef carol;

    @BeforeEach
    void setUp() {
        repo = new InMemoryWalletRepository();
        sink = new CapturingSink();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
        carol = new PlayerRef(UUID.randomUUID(), "Carol");
    }

    private PayAll payAllWith() {
        CurrencyRegistry registry = CurrencyRegistry.single(Currencies.COINS);
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        NativeEconomyProvider provider = new NativeEconomyProvider(repo, registry, clock);
        EconomyNotifier notifier = new EconomyNotifier(new KeyMessages(), sink);
        Pay pay = new Pay(provider, new InMemoryPayPreferences(), new InMemoryPendingPayRegistry(), notifier, clock);
        return new PayAll(pay, notifier);
    }

    @Test
    void paysEachRecipientAndSkipsTheSender() {
        Currency coins = Currencies.COINS;
        repo.credit(alice, Money.of(coins, 100));
        PayAll payAll = payAllWith();

        payAll.payAll(alice, List.of(alice, bob, carol), Money.of(coins, 10));

        // Alice is skipped; she is debited 10 for Bob and 10 for Carol.
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 80));
        assertThat(repo.findByOwner(bob).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 10));
        assertThat(repo.findByOwner(carol).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 10));
        assertThat(sink.delivered("wallet.payall-sent")).isTrue();
    }

    @Test
    void recipientsBeyondTheSendersFundsAreNotOverdrawn() {
        Currency coins = Currencies.COINS;
        repo.credit(alice, Money.of(coins, 15));
        PayAll payAll = payAllWith();

        payAll.payAll(alice, List.of(bob, carol), Money.of(coins, 10));

        // Only the first recipient is affordable; the second leg is rejected by Pay (no overdraw). Carol's
        // row may be materialised by the rejected transfer, but she is never credited.
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 5));
        assertThat(repo.findByOwner(bob).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 10));
        assertThat(repo.findByOwner(carol).map(wallet -> wallet.balanceOf(coins)))
                .hasValueSatisfying(balance -> assertThat(balance).isEqualTo(Money.of(coins, 0)));
    }
}
