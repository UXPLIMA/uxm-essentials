package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.persistence.economy.CachedWalletRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.network.AllBalancesChanged;
import com.uxplima.uxmessentials.shared.network.BalanceChanged;
import org.jspecify.annotations.NullMarked;

/**
 * The economy context's cross-server sync seam: the second representative wiring alongside {@link HomeSync}.
 * It wraps the cached {@link WalletRepository} so every local balance mutation publishes a
 * {@link BalanceChanged} frame after the guarded transaction commits, and returns a {@link RemoteSyncListener}
 * that drops the affected owner from the same cache on a remote change.
 *
 * <p>The frame is a notification, not the figure: the cross-server transfer's debit/credit is one atomic
 * transaction against the shared row, and the bus only invalidates the
 * peer's read cache so it never serves a stale balance. The {@code upsertBalance} coalesced-settle path also
 * announces, so a flushed credit reaches peers. The guarded debit/credit/transfer themselves are unchanged
 * the decorator forwards to the same repository; it only appends the announcement.
 *
 * <p>A sweep over every account changes rows this cache and every peer's may hold, so it drops the whole local cache
 * and sends one {@link AllBalancesChanged} for peers to do the same.
 */
@NullMarked
public final class WalletSync {

    private WalletSync() {}

    /** A {@link WalletRepository} that broadcasts a {@link BalanceChanged} after every local balance write. */
    public static WalletRepository repository(WalletRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /**
     * An {@link AccountSweep} that, once a sweep has run, drops every wallet {@code cache} holds and tells peers to
     * drop theirs. It does both even when the sweep failed: a failure at commit leaves no way to tell whether the rows
     * changed, and a needless drop only costs a read.
     */
    public static AccountSweep sweep(AccountSweep delegate, CachedWalletRepository cache, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(bus, "bus");
        return new AnnouncingSweep(delegate, cache, bus);
    }

    /**
     * A listener that invalidates the affected owner's cached wallet on a remote {@link BalanceChanged}, and every
     * cached wallet on a remote {@link AllBalancesChanged}.
     */
    public static RemoteSyncListener listener(CachedWalletRepository cache) {
        Objects.requireNonNull(cache, "cache");
        return message -> {
            if (message instanceof BalanceChanged changed) {
                cache.invalidateOwner(changed.owner());
            } else if (message instanceof AllBalancesChanged) {
                cache.invalidateAll();
            }
        };
    }

    /** Runs each sweep through the delegate, then drops the local cache and announces the sweep to peers. */
    private static final class AnnouncingSweep implements AccountSweep {

        private final AccountSweep delegate;
        private final CachedWalletRepository cache;
        private final BusPublisher bus;

        AnnouncingSweep(AccountSweep delegate, CachedWalletRepository cache, BusPublisher bus) {
            this.delegate = delegate;
            this.cache = cache;
            this.bus = bus;
        }

        @Override
        public boolean reaches(Currency currency) {
            return delegate.reaches(currency);
        }

        @Override
        public SweepOutcome creditEvery(Money amount, EconomyReason reason, long at) {
            try {
                return delegate.creditEvery(amount, reason, at);
            } finally {
                announce(amount.currency());
            }
        }

        @Override
        public SweepOutcome resetEvery(Money balance, EconomyReason reason, long at) {
            try {
                return delegate.resetEvery(balance, reason, at);
            } finally {
                announce(balance.currency());
            }
        }

        private void announce(Currency currency) {
            cache.invalidateAll();
            bus.publish(new AllBalancesChanged(bus.serverId(), currency.id().value()));
        }
    }

    /** Forwards every call to the cached delegate, announcing each balance mutation on the bus. */
    private static final class Broadcasting implements WalletRepository {

        private final WalletRepository delegate;
        private final BusPublisher bus;

        Broadcasting(WalletRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            return delegate.findByOwner(owner);
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            return delegate.ensureOwner(owner);
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {
            delegate.upsertBalance(owner, balance);
            announce(owner, balance.currency());
        }

        @Override
        public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
            Result<Unit, TransferError> result = delegate.transfer(from, to, amount);
            if (result.isOk()) {
                announce(from, amount.currency());
                announce(to, amount.currency());
            }
            return result;
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            Result<Unit, TransferError> result = delegate.debit(owner, amount);
            if (result.isOk()) {
                announce(owner, amount.currency());
            }
            return result;
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            Result<Unit, TransferError> result = delegate.credit(owner, amount);
            if (result.isOk()) {
                announce(owner, amount.currency());
            }
            return result;
        }

        @Override
        public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
            Result<Unit, TransferError> result = delegate.exchange(owner, debit, credit);
            if (result.isOk()) {
                announce(owner, debit.currency());
                announce(owner, credit.currency());
            }
            return result;
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return delegate.top(currency, limit);
        }

        private void announce(PlayerRef owner, Currency currency) {
            bus.publish(new BalanceChanged(
                    bus.serverId(), owner.uuid(), currency.id().value()));
        }
    }
}
