package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class SerialisingCurrencyBackendTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void anAtomicBackendIsReturnedUnwrapped() {
        CurrencyBackend atomic = new FakeCurrencyBackend("native", true, true);
        assertThat(SerialisingCurrencyBackend.wrapIfNeeded(atomic)).isSameAs(atomic);
    }

    @Test
    void aNonAtomicBackendIsWrappedAndKeepsItsIdentity() {
        CurrencyBackend wrapped =
                SerialisingCurrencyBackend.wrapIfNeeded(new FakeCurrencyBackend("vault", false, true));
        assertThat(wrapped).isInstanceOf(SerialisingCurrencyBackend.class);
        assertThat(wrapped.id()).isEqualTo("vault");
        assertThat(wrapped.atomicDebit()).isFalse();
        assertThat(wrapped.worksOffline()).isTrue();
    }

    @RepeatedTest(50)
    void concurrentDebitsThroughTheWrapperNeverOverdraw() throws Exception {
        FakeCurrencyBackend raw = new FakeCurrencyBackend("vault", false, true);
        raw.seed(ALICE, new BigDecimal("100"));
        CurrencyBackend backend = SerialisingCurrencyBackend.wrapIfNeeded(raw);

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> jobs = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                jobs.add(() -> backend.debit(ALICE, Money.of(COINS, new BigDecimal("10")))
                        .isOk());
            }
            long succeeded = pool.invokeAll(jobs).stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .filter(Boolean::booleanValue)
                    .count();
            assertThat(succeeded).isEqualTo(10);
        }
        assertThat(raw.balance(ALICE, COINS).amount()).isEqualByComparingTo("0");
    }
}
