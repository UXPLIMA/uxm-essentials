package com.uxplima.uxmessentials.vaults.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.Test;

/** A vault refund lands in the currency the vault was paid in, or nowhere; a fee is recorded as it was taken. */
class ProviderVaultEconomyTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).precision(2).build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();

    private final Bank bank = new Bank();
    private final ProviderVaultEconomy economy = new ProviderVaultEconomy(bank, COINS, Optional.empty());

    @Test
    void feesAreChargedAndNamedInTheServersCurrency() {
        assertThat(economy.currencyId()).isEqualTo("coins");
        assertThat(economy.denominate(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
    }

    @Test
    void aRefundGoesBackInTheCurrencyTheVaultWasPaidIn() {
        assertThat(economy.deposit(ALEX, new BigDecimal("5"), "gems")).isTrue();

        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("5");
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void aRefundInACurrencyTheEconomyNoLongerHasIsDroppedNotPaidInTheDefault() {
        assertThat(economy.deposit(ALEX, new BigDecimal("5"), "shells")).isFalse();

        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("0");
        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("0");
    }

    /** Balances in two currencies. */
    private static final class Bank implements EconomyProvider {

        private final Map<Currency, BigDecimal> held = new HashMap<>();

        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return true;
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {}

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return Money.of(currency, held.getOrDefault(currency, BigDecimal.ZERO));
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            held.merge(amount.currency(), amount.amount(), BigDecimal::add);
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            held.merge(amount.currency(), amount.amount().negate(), BigDecimal::add);
            return Result.ok();
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException("a vault never transfers");
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public Set<Currency> currencies() {
            return Set.of(COINS, GEMS);
        }
    }
}
