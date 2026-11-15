package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/** An entry fee comes back in the currency it was taken in, or not at all. */
class WorldEntryFeeRefundTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();

    private final Bank bank = new Bank();

    @Test
    void aFeeTakenInAnotherCurrencyThanThisServersComesBackInThatCurrency() {
        EconomyWorldEntryFee fee = new EconomyWorldEntryFee(bank, COINS);

        boolean refunded = fee.refund(ALEX, new BigDecimal("5"), "gems");

        assertThat(refunded).isTrue();
        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("5");
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void aFeeInACurrencyThisServerDoesNotKnowIsNotCredited() {
        EconomyWorldEntryFee fee = new EconomyWorldEntryFee(bank, COINS);

        boolean refunded = fee.refund(ALEX, new BigDecimal("5"), "shells");

        assertThat(refunded).isFalse();
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void withNoEconomyOnlyANothingIsGivenBack() {
        FreeWorldEntryFee free = new FreeWorldEntryFee();

        assertThat(free.refund(ALEX, new BigDecimal("5"), "coins")).isFalse();
        assertThat(free.refund(ALEX, BigDecimal.ZERO, "coins")).isTrue();
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
            throw new UnsupportedOperationException("a refund never transfers");
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
