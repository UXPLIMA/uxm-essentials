package com.uxplima.uxmessentials.economy.adapter.outbound;

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

/**
 * Routes {@code top(currency, limit)} through the per-currency {@link BaltopSnapshots} (cached, exempt-
 * filtered, refreshed off-tick) while passing every other {@link EconomyProvider} call straight to the
 * resolved provider. This is the seam that gives the {@code BalTop} use case the budgeted, lock-free
 * {@code /baltop} read-model of {@code docs/11-economy-integration.md} §11 without the use case knowing
 * whether the snapshot or the provider produced the rows.
 *
 * <p>It is a read decorator only: balances, credits, debits, and transfers are the live provider's, so the
 * double-spend guard and the offline-read cache are unaffected; only the leaderboard read is served from the
 * snapshot.
 */
@NullMarked
public final class SnapshotBaltopProvider implements EconomyProvider {

    private final EconomyProvider delegate;
    private final BaltopSnapshots snapshots;

    public SnapshotBaltopProvider(EconomyProvider delegate, BaltopSnapshots snapshots) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        return snapshots.top(currency, limit);
    }

    @Override
    public boolean hasAccount(PlayerRef owner, Currency currency) {
        return delegate.hasAccount(owner, currency);
    }

    @Override
    public void ensureAccount(PlayerRef owner, Currency currency) {
        delegate.ensureAccount(owner, currency);
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        return delegate.balance(owner, currency);
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        return delegate.credit(owner, amount);
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        return delegate.debit(owner, amount);
    }

    @Override
    public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
        return delegate.transfer(from, to, amount);
    }

    @Override
    public Set<Currency> currencies() {
        return delegate.currencies();
    }
}
