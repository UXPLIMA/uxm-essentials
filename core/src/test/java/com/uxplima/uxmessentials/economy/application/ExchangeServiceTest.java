package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
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

    private ExchangeService serviceWith(List<ExchangeRate> rates, boolean nativeLedger) {
        return new ExchangeService(repo, new ExchangeRegistry(rates), nativeLedger);
    }

    @Test
    void exchangeSuccessfulMovesBalancesAtomically() {
        ExchangeRate rate = new ExchangeRate(coins.id(), gems.id(), BigDecimal.valueOf(0.01), BigDecimal.valueOf(0.05));
        ExchangeService service = serviceWith(List.of(rate), true);

        repo.credit(alice, Money.of(coins, 200));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(100), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.SUCCESS);
        assertThat(outcome.sourceAmount()).isEqualTo(BigDecimal.valueOf(100));
        assertThat(outcome.targetAmount()).isEqualTo(BigDecimal.ONE); // (100 * 0.01) - 5% = 0.95 -> rounded to 1

        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 100));
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(gems)).isEqualTo(Money.of(gems, 1));
    }

    @Test
    void exchangeRateNotFoundMovesNothing() {
        ExchangeService service = serviceWith(List.of(), true);
        repo.credit(alice, Money.of(coins, 200));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(100), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.RATE_NOT_FOUND);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 200));
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(gems)).isEqualTo(Money.of(gems, 0));
    }

    @Test
    void insufficientSourceCreditsNothing() {
        ExchangeRate rate = new ExchangeRate(coins.id(), gems.id(), BigDecimal.valueOf(0.01), BigDecimal.ZERO);
        ExchangeService service = serviceWith(List.of(rate), true);

        repo.credit(alice, Money.of(coins, 50));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(100), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.INSUFFICIENT_FUNDS);
        // The debit side was short, so the target currency was never credited and the source is untouched.
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 50));
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(gems)).isEqualTo(Money.of(gems, 0));
    }

    @Test
    void targetOverMaxLeavesSourceUntouched() {
        ExchangeRate rate = new ExchangeRate(coins.id(), gems.id(), BigDecimal.ONE, BigDecimal.ZERO);
        ExchangeService service = serviceWith(List.of(rate), true);

        repo.credit(alice, Money.of(coins, 200));
        repo.credit(alice, Money.of(gems, 50));

        // 150 coins -> 150 gems would push the gems balance (50 + 150) past the max of 100: refused atomically.
        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(150), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.LIMIT_EXCEEDED);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 200));
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(gems)).isEqualTo(Money.of(gems, 50));
    }

    @Test
    void exchangeDisabledCurrencyRefusesWithoutMoving() {
        Currency lockedGems = Currency.builder(CurrencyId.of("gems"))
                .precision(0)
                .max(BigDecimal.valueOf(100))
                .exchangeAllowed(false)
                .build();
        ExchangeRate rate = new ExchangeRate(coins.id(), lockedGems.id(), BigDecimal.ONE, BigDecimal.ZERO);
        ExchangeService service = serviceWith(List.of(rate), true);
        repo.credit(alice, Money.of(coins, 200));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(50), coins, lockedGems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.CURRENCY_DISABLED);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 200));
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(lockedGems)).isEqualTo(Money.of(lockedGems, 0));
    }

    @Test
    void foreignProviderRefusesWithoutMoving() {
        ExchangeRate rate = new ExchangeRate(coins.id(), gems.id(), BigDecimal.ONE, BigDecimal.ZERO);
        ExchangeService service = serviceWith(List.of(rate), false);

        repo.credit(alice, Money.of(coins, 200));

        ExchangeOutcome outcome = service.exchange(alice, BigDecimal.valueOf(50), coins, gems);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.PROVIDER_UNSUPPORTED);
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(coins)).isEqualTo(Money.of(coins, 200));
        assertThat(repo.findByOwner(alice).orElseThrow().balanceOf(gems)).isEqualTo(Money.of(gems, 0));
    }
}
