package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * An in-memory {@link CurrencyBackend} for the façade and menu-vocabulary tests. Balances are keyed by UUID so a
 * test can seed and assert them the way the menu surface addresses players; {@code deposits}/{@code withdrawals}
 * count the moves the façade drove, which the set-money no-op case asserts stay at zero.
 */
public final class FakeCurrencyBackend implements CurrencyBackend {

    public final Map<UUID, Double> balances = new HashMap<>();
    public int deposits;
    public int withdrawals;

    private final String id;
    private final Precision precision;
    private final boolean available;

    public FakeCurrencyBackend(String id) {
        this(id, Precision.DECIMAL, true);
    }

    public FakeCurrencyBackend(String id, Precision precision, boolean available) {
        this.id = Objects.requireNonNull(id, "id");
        this.precision = Objects.requireNonNull(precision, "precision");
        this.available = available;
    }

    public void seed(UUID owner, double amount) {
        balances.put(Objects.requireNonNull(owner, "owner"), amount);
    }

    public void seed(PlayerRef owner, BigDecimal amount) {
        balances.put(owner.uuid(), amount.doubleValue());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public boolean worksOffline() {
        return true;
    }

    @Override
    public boolean atomicDebit() {
        return true;
    }

    @Override
    public Precision precision() {
        return precision;
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        return Money.of(currency, BigDecimal.valueOf(balances.getOrDefault(owner.uuid(), 0.0)));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        deposits++;
        balances.merge(owner.uuid(), amount.amount().doubleValue(), Double::sum);
        return Result.ok();
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        double current = balances.getOrDefault(owner.uuid(), 0.0);
        if (current < amount.amount().doubleValue()) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        withdrawals++;
        balances.put(owner.uuid(), current - amount.amount().doubleValue());
        return Result.ok();
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        List<BaltopRow> rows = new ArrayList<>();
        balances.forEach((uuid, amount) ->
                rows.add(new BaltopRow(new PlayerRef(uuid, ""), Money.of(currency, BigDecimal.valueOf(amount)))));
        rows.sort((a, b) -> b.balance().amount().compareTo(a.balance().amount()));
        return rows.size() <= limit ? rows : rows.subList(0, limit);
    }
}
