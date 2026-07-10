package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.economy.fakes.RecordingLogger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class RoutingEconomyProviderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC);
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Currency POINTS =
            Currency.builder(CurrencyId.of("points")).backendId("playerpoints").build();
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    @Test
    void routesEachCurrencyToItsDeclaredBackend() {
        FakeCurrencyBackend nativeBackend = new FakeCurrencyBackend("native");
        FakeCurrencyBackend points = new FakeCurrencyBackend("playerpoints");
        RoutingEconomyProvider provider = provider(nativeBackend, points);

        provider.credit(ALICE, Money.of(POINTS, new BigDecimal("7")));

        assertThat(points.balance(ALICE, POINTS).amount()).isEqualByComparingTo("7");
        assertThat(nativeBackend.balance(ALICE, COINS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void anUnknownBackendIsAStartupErrorNotASilentDefault() {
        Currency broken =
                Currency.builder(CurrencyId.of("broken")).backendId("nope").build();
        CurrencyRegistry currencies = CurrencyRegistry.of(List.of(COINS, broken), CurrencyId.of("coins"));
        CurrencyBackendRegistry backends = CurrencyBackendRegistry.of(List.of(new FakeCurrencyBackend("native")));

        assertThatThrownBy(() -> new RoutingEconomyProvider(backends, currencies, CLOCK, new RecordingLogger()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broken")
                .hasMessageContaining("nope");
    }

    @Test
    void selfTransferIsDenied() {
        RoutingEconomyProvider provider =
                provider(new FakeCurrencyBackend("native"), new FakeCurrencyBackend("playerpoints"));
        assertThat(provider.transfer(ALICE, ALICE, Money.of(COINS, new BigDecimal("1"))))
                .isInstanceOf(TransferResult.DenyWith.class);
    }

    @Test
    void aFailedCreditLegCompensatesTheDebitLeg() {
        RecordingLogger log = new RecordingLogger();
        FakeCurrencyBackend backend = new FakeCurrencyBackend("native");
        backend.seed(ALICE, new BigDecimal("100"));
        backend.seed(BOB, new BigDecimal("100"));
        Currency capped = Currency.builder(CurrencyId.of("coins"))
                .max(new BigDecimal("150"))
                .build();
        RoutingEconomyProvider provider = new RoutingEconomyProvider(
                CurrencyBackendRegistry.of(List.of(backend)),
                CurrencyRegistry.of(List.of(capped), CurrencyId.of("coins")),
                CLOCK,
                log);

        // Bob is already at the cap, so the credit leg overflows; the debit of Alice must be rolled back.
        TransferResult result = provider.transfer(ALICE, BOB, Money.of(capped, new BigDecimal("100")));

        assertThat(result).isInstanceOf(TransferResult.InsufficientFunds.class);
        assertThat(backend.balance(ALICE, capped).amount()).isEqualByComparingTo("100");
        assertThat(backend.balance(BOB, capped).amount()).isEqualByComparingTo("100");
        assertThat(log.errors()).isEmpty();
    }

    @Test
    void aCompensationThatAlsoFailsIsLoggedAtError() {
        RecordingLogger log = new RecordingLogger();
        FakeCurrencyBackend backend = new FakeCurrencyBackend("native");
        backend.seed(ALICE, new BigDecimal("100"));
        Currency capped = Currency.builder(CurrencyId.of("coins"))
                .max(new BigDecimal("5"))
                .build();
        RoutingEconomyProvider provider = new RoutingEconomyProvider(
                CurrencyBackendRegistry.of(List.of(backend)),
                CurrencyRegistry.of(List.of(capped), CurrencyId.of("coins")),
                CLOCK,
                log);

        // Alice holds an above-cap balance, so refunding her the debit overflows the cap too: she stays short.
        TransferResult result = provider.transfer(ALICE, BOB, Money.of(capped, new BigDecimal("100")));

        assertThat(result).isInstanceOf(TransferResult.InsufficientFunds.class);
        assertThat(backend.balance(ALICE, capped).amount()).isEqualByComparingTo("0");
        assertThat(backend.balance(BOB, capped).amount()).isEqualByComparingTo("0");
        assertThat(log.errors()).hasSize(1);
        assertThat(log.errors().get(0)).contains("Alice").contains("coins");
    }

    @Test
    void aNativeTransferTakesTheAtomicRepositoryPathNotCompensation() {
        RecordingLogger log = new RecordingLogger();
        NativeCurrencyBackend backend = new NativeCurrencyBackend(new InMemoryWalletRepository());
        Currency capped = Currency.builder(CurrencyId.of("coins"))
                .max(new BigDecimal("150"))
                .build();
        RoutingEconomyProvider provider = new RoutingEconomyProvider(
                CurrencyBackendRegistry.of(List.of(backend)),
                CurrencyRegistry.of(List.of(capped), CurrencyId.of("coins")),
                CLOCK,
                log);
        provider.credit(ALICE, Money.of(capped, new BigDecimal("100")));
        provider.credit(BOB, Money.of(capped, new BigDecimal("100")));

        // The native ledger moves both legs in one repository transaction, never through the compensating
        // credit path — so the transfer commits (and, because the in-memory repository does not model the
        // recipient max clamp, Bob ends above the cap) and nothing is logged.
        TransferResult result = provider.transfer(ALICE, BOB, Money.of(capped, new BigDecimal("100")));

        assertThat(result.isOk()).isTrue();
        assertThat(provider.balance(ALICE, capped).amount()).isEqualByComparingTo("0");
        assertThat(provider.balance(BOB, capped).amount()).isEqualByComparingTo("200");
        assertThat(log.errors()).isEmpty();
    }

    private static RoutingEconomyProvider provider(FakeCurrencyBackend nativeBackend, FakeCurrencyBackend other) {
        CurrencyRegistry currencies = CurrencyRegistry.of(List.of(COINS, POINTS), CurrencyId.of("coins"));
        return new RoutingEconomyProvider(
                CurrencyBackendRegistry.of(List.of(nativeBackend, other)), currencies, CLOCK, new RecordingLogger());
    }
}
