package com.uxplima.uxmessentials.shared.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderWarpEconomy;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.homes.adapter.outbound.ProviderHomeEconomy;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.Test;

/**
 * A warp or home charged on one server is given back in the currency it was taken in, on whichever server the refund
 * runs, and a currency the refunding server does not know is never credited in another one.
 */
class HopRefundCurrencyTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();
    private static final BigDecimal FEE = new BigDecimal("10");

    /** One shared database of balances, as a network has. */
    private final Bank bank = new Bank();

    @Test
    void aWarpChargedInTheOriginsDefaultIsRefundedInThatCurrencyWhereTheDefaultDiffers() {
        bank.credit(ALEX, Money.of(COINS, new BigDecimal("50")));
        ProviderWarpEconomy origin = new ProviderWarpEconomy(bank, COINS, Optional.empty());
        ProviderWarpEconomy target = new ProviderWarpEconomy(bank, GEMS, Optional.empty());

        Optional<String> takenIn = origin.debit(ALEX, FEE, "default");
        boolean refunded = target.refund(ALEX, FEE, takenIn.orElseThrow());

        assertThat(takenIn).contains("coins");
        assertThat(refunded).isTrue();
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("50");
        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void aHomeChargedInTheOriginsDefaultIsRefundedInThatCurrencyWhereTheDefaultDiffers() {
        bank.credit(ALEX, Money.of(COINS, new BigDecimal("50")));
        ProviderHomeEconomy origin = new ProviderHomeEconomy(bank, COINS, Optional.empty());
        ProviderHomeEconomy target = new ProviderHomeEconomy(bank, GEMS, Optional.empty());

        boolean refunded =
                target.refund(ALEX, FEE, origin.debit(ALEX, FEE, "default").orElseThrow());

        assertThat(refunded).isTrue();
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("50");
        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void aCurrencyTheRefundingServerDoesNotKnowIsNotCreditedInItsDefault() {
        ProviderWarpEconomy warps = new ProviderWarpEconomy(bank, COINS, Optional.empty());
        ProviderHomeEconomy homes = new ProviderHomeEconomy(bank, COINS, Optional.empty());

        assertThat(warps.refund(ALEX, FEE, "shells")).isFalse();
        assertThat(homes.refund(ALEX, FEE, "shells")).isFalse();
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void aChargeThePlayerCannotAffordTakesNothingAndNamesNoCurrency() {
        ProviderWarpEconomy warps = new ProviderWarpEconomy(bank, COINS, Optional.empty());

        assertThat(warps.debit(ALEX, FEE, "default")).isEmpty();
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("0");
    }

    /** Balances in two currencies; a debit past the balance is refused. */
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
            BigDecimal now = held.getOrDefault(amount.currency(), BigDecimal.ZERO);
            if (now.compareTo(amount.amount()) < 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            held.put(amount.currency(), now.subtract(amount.amount()));
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
