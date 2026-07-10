package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 * An in-memory backend for tests. {@code atomic} controls whether {@link #debit} performs its check and its
 * write as one indivisible step; when false it deliberately reads, yields, then writes, so a test can drive
 * the lost-update race the serialising decorator exists to prevent.
 */
public final class FakeCurrencyBackend implements CurrencyBackend {

    private final String id;
    private final boolean atomic;
    private final boolean offline;
    private final Map<PlayerRef, BigDecimal> balances = new HashMap<>();

    public FakeCurrencyBackend(String id) {
        this(id, true, true);
    }

    public FakeCurrencyBackend(String id, boolean atomic, boolean offline) {
        this.id = Objects.requireNonNull(id, "id");
        this.atomic = atomic;
        this.offline = offline;
    }

    public void seed(PlayerRef owner, BigDecimal amount) {
        balances.put(owner, amount);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean worksOffline() {
        return offline;
    }

    @Override
    public boolean atomicDebit() {
        return atomic;
    }

    @Override
    public Precision precision() {
        return Precision.DECIMAL;
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        return Money.of(currency, balances.getOrDefault(owner, BigDecimal.ZERO));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        synchronized (balances) {
            BigDecimal current = balances.getOrDefault(owner, BigDecimal.ZERO);
            if (current.add(amount.amount()).compareTo(amount.currency().max()) > 0) {
                return Result.err(TransferError.BALANCE_MAX_EXCEEDED);
            }
            balances.put(owner, current.add(amount.amount()));
        }
        return Result.ok();
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        if (atomic) {
            synchronized (balances) {
                return applyDebit(owner, amount);
            }
        }
        BigDecimal current = balances.getOrDefault(owner, BigDecimal.ZERO);
        if (current.compareTo(amount.amount()) < 0) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        Thread.onSpinWait();
        balances.put(owner, current.subtract(amount.amount()));
        return Result.ok();
    }

    private Result<Unit, TransferError> applyDebit(PlayerRef owner, Money amount) {
        BigDecimal current = balances.getOrDefault(owner, BigDecimal.ZERO);
        if (current.compareTo(amount.amount()) < 0) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        balances.put(owner, current.subtract(amount.amount()));
        return Result.ok();
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        List<BaltopRow> rows = new ArrayList<>();
        balances.forEach((owner, amount) -> rows.add(new BaltopRow(owner, Money.of(currency, amount))));
        rows.sort((a, b) -> b.balance().amount().compareTo(a.balance().amount()));
        return rows.size() <= limit ? rows : rows.subList(0, limit);
    }
}
