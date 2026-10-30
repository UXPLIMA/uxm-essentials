package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.NOW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/** Balances per player and currency, transferring only when the payer can cover it. */
final class InMemoryBank implements EconomyProvider {

    private final Set<Currency> currencies;
    private final Map<String, Money> balances = new HashMap<>();

    InMemoryBank(Currency... currencies) {
        this.currencies = Set.of(currencies);
    }

    void set(PlayerRef owner, Money amount) {
        balances.put(owner.uuid() + amount.currency().id().value(), amount);
    }

    @Override
    public boolean hasAccount(PlayerRef owner, Currency currency) {
        return true;
    }

    @Override
    public void ensureAccount(PlayerRef owner, Currency currency) {}

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        return balances.getOrDefault(owner.uuid() + currency.id().value(), Money.zero(currency));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        set(owner, balance(owner, amount.currency()).plus(amount));
        return Result.ok();
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Money have = balance(owner, amount.currency());
        if (have.isLessThan(amount)) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        set(owner, have.minus(amount));
        return Result.ok();
    }

    @Override
    public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
        Money have = balance(from, amount.currency());
        if (have.isLessThan(amount)) {
            return TransferResult.insufficientFunds(amount, have);
        }
        debit(from, amount);
        credit(to, amount);
        return TransferResult.allow(
                Transaction.debit(from, amount, balance(from, amount.currency()), NOW),
                Transaction.credit(to, amount, balance(to, amount.currency()), NOW));
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        return List.of();
    }

    @Override
    public Set<Currency> currencies() {
        return currencies;
    }
}
