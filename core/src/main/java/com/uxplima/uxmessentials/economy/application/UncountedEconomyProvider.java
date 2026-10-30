package com.uxplima.uxmessentials.economy.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/** A provider whose every change of money is {@link UncountedMoney not counted}: the one {@code /eco} works through. */
@NullMarked
public final class UncountedEconomyProvider implements EconomyProvider {

    private final EconomyProvider inner;

    public UncountedEconomyProvider(EconomyProvider inner) {
        this.inner = Objects.requireNonNull(inner, "inner");
    }

    @Override
    public boolean hasAccount(PlayerRef owner, Currency currency) {
        return inner.hasAccount(owner, currency);
    }

    @Override
    public void ensureAccount(PlayerRef owner, Currency currency) {
        inner.ensureAccount(owner, currency);
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        return inner.balance(owner, currency);
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        return UncountedMoney.call(() -> inner.credit(owner, amount));
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        return UncountedMoney.call(() -> inner.debit(owner, amount));
    }

    @Override
    public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
        return UncountedMoney.call(() -> inner.transfer(from, to, amount));
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        return inner.top(currency, limit);
    }

    @Override
    public Set<Currency> currencies() {
        return inner.currencies();
    }
}
