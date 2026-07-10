package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class NativeCurrencyBackendTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void identifiesItselfAsTheAtomicOfflineCapableLedger() {
        NativeCurrencyBackend backend = new NativeCurrencyBackend(new InMemoryWalletRepository());
        assertThat(backend.id()).isEqualTo("native");
        assertThat(backend.atomicDebit()).isTrue();
        assertThat(backend.worksOffline()).isTrue();
        assertThat(backend.precision()).isEqualTo(Precision.DECIMAL);
        assertThat(backend.available()).isTrue();
    }

    @Test
    void delegatesEveryReadAndWriteToTheRepository() {
        InMemoryWalletRepository repo = new InMemoryWalletRepository();
        NativeCurrencyBackend backend = new NativeCurrencyBackend(repo);

        backend.credit(ALICE, Money.of(COINS, new BigDecimal("100")));
        assertThat(backend.balance(ALICE, COINS)).isEqualTo(Money.of(COINS, new BigDecimal("100")));

        assertThat(backend.debit(ALICE, Money.of(COINS, new BigDecimal("40"))).isOk())
                .isTrue();
        assertThat(backend.balance(ALICE, COINS)).isEqualTo(Money.of(COINS, new BigDecimal("60")));
    }

    @Test
    void rejectsAnOverdraftWithoutMutating() {
        InMemoryWalletRepository repo = new InMemoryWalletRepository();
        NativeCurrencyBackend backend = new NativeCurrencyBackend(repo);
        backend.credit(ALICE, Money.of(COINS, new BigDecimal("10")));

        assertThat(backend.debit(ALICE, Money.of(COINS, new BigDecimal("11"))).isErr())
                .isTrue();
        assertThat(backend.balance(ALICE, COINS)).isEqualTo(Money.of(COINS, new BigDecimal("10")));
    }
}
