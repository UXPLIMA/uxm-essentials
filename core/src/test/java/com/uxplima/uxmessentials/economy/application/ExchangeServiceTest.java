package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.ExchangeRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExchangeServiceTest {

    private InMemoryWalletRepository repo;
    private PlayerRef alice;
    private Currency coins;
    private Currency gems;

    @BeforeEach
    void setUp() {
        repo = new InMemoryWalletRepository();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        coins = Currencies.COINS;
        gems = Currency.builder(CurrencyId.of("gems"))
                .symbol("✦")
                .precision(0)
                .starting(BigDecimal.ZERO)
                .max(BigDecimal.valueOf(100))
                .build();
    }

    private ExchangeService serviceWith(List<ExchangeRate> rates) {
        CurrencyRegistry registry = CurrencyRegistry.of(Set.of(coins, gems), coins.id());
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        NativeEconomyProvider provider = new NativeEconomyProvider(repo, registry, clock);
        ExchangeRegistry exchangeRegistry = new ExchangeRegistry(rates);
        return new ExchangeService(provider, exchangeRegistry);
    }

    @Test
    void exchangeSuccessfulMovesBalances() {
        ExchangeRate rate = new ExchangeRate(coins.id(), gems.id(), BigDecimal.valueOf(0.01), BigDecimal.valueOf(0.05));
        ExchangeService service = serviceWith(List.of(rate));

        repo.credit(alice, Money.of(coins, 200));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(100), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.SUCCESS);
        assertThat(outcome.sourceAmount()).isEqualTo(BigDecimal.valueOf(100));
        assertThat(outcome.targetAmount()).isEqualTo(BigDecimal.ONE); // (100 * 0.01) - 5% = 0.95 -> rounded to 1

        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 100));
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(gems)).isEqualTo(Money.of(gems, 1));
    }

    @Test
    void exchangeRateNotFoundReturnsStatus() {
        ExchangeService service = serviceWith(List.of());
        repo.credit(alice, Money.of(coins, 200));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(100), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.RATE_NOT_FOUND);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 200));
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(gems)).isEqualTo(Money.of(gems, 0));
    }

    @Test
    void exchangeInsufficientFundsReturnsStatus() {
        ExchangeRate rate = new ExchangeRate(coins.id(), gems.id(), BigDecimal.valueOf(0.01), BigDecimal.ZERO);
        ExchangeService service = serviceWith(List.of(rate));

        repo.credit(alice, Money.of(coins, 50));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(100), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.INSUFFICIENT_FUNDS);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 50));
    }

    @Test
    void rollbackFailureIsSurfacedAsItsOwnStatus() {
        ExchangeRate rate = new ExchangeRate(coins.id(), gems.id(), BigDecimal.ONE, BigDecimal.ZERO);
        CurrencyRegistry registry = CurrencyRegistry.of(Set.of(coins, gems), coins.id());
        // A provider that debits fine, fails the credit, and also fails the compensating rollback credit.
        com.uxplima.uxmessentials.economy.application.port.EconomyProvider provider =
                new FailingRollbackProvider(registry);
        ExchangeService service = new ExchangeService(provider, new ExchangeRegistry(List.of(rate)));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(10), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.ROLLBACK_FAILED);
        assertThat(outcome.sourceAmount()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(outcome.error())
                .isEqualTo(com.uxplima.uxmessentials.economy.domain.TransferError.BALANCE_MAX_EXCEEDED);
    }

    /** Debits succeed; every credit is rejected, so the rollback credit fails too — the money-lost case. */
    private static final class FailingRollbackProvider
            implements com.uxplima.uxmessentials.economy.application.port.EconomyProvider {
        private final CurrencyRegistry registry;

        FailingRollbackProvider(CurrencyRegistry registry) {
            this.registry = registry;
        }

        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return true;
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {}

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return Money.zero(currency);
        }

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<
                        com.uxplima.uxmessentials.shared.domain.Unit,
                        com.uxplima.uxmessentials.economy.domain.TransferError>
                credit(PlayerRef owner, Money amount) {
            return com.uxplima.uxmessentials.shared.domain.Result.err(
                    com.uxplima.uxmessentials.economy.domain.TransferError.BALANCE_MAX_EXCEEDED);
        }

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<
                        com.uxplima.uxmessentials.shared.domain.Unit,
                        com.uxplima.uxmessentials.economy.domain.TransferError>
                debit(PlayerRef owner, Money amount) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public com.uxplima.uxmessentials.economy.domain.TransferResult transfer(
                PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.uxplima.uxmessentials.economy.application.port.BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public Set<Currency> currencies() {
            return registry.all();
        }
    }

    @Test
    void exchangeRollingBackOnCreditFailure() {
        ExchangeRate rate = new ExchangeRate(coins.id(), gems.id(), BigDecimal.ONE, BigDecimal.ZERO);
        ExchangeService service = serviceWith(List.of(rate));

        repo.credit(alice, Money.of(coins, 200));
        repo.credit(alice, Money.of(gems, 50));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(150), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.LIMIT_EXCEEDED);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 200));
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(gems)).isEqualTo(Money.of(gems, 50));
    }
}
