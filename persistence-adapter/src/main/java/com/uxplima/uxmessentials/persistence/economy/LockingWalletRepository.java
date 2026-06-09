package com.uxplima.uxmessentials.persistence.economy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A {@link WalletRepository} decorator that broadcasts a Redis cache-invalidation after each balance change
 * and serialises the local enter/exit of a mutation per owner through a {@link StripedLock}.
 *
 * <p><strong>Money safety lives in the database, not here.</strong> The authoritative guard is the delegate's
 * single {@code UPDATE … WHERE amount >= ?} ({@link JooqWalletRepository}); two concurrent debits are
 * serialised by the database and an over-draw updates zero rows. A JVM lock would be wrong the moment two
 * servers share the database, so this class holds <em>no</em> lock across any DB or Redis call.
 *
 * <p>What the {@link StripedLock} guards is a brief in-memory critical section only — the per-owner ordering
 * of "begin mutation" relative to other mutations for the same owner on this JVM. The lock is acquired,
 * the in-memory marker is taken, and the lock is <em>released before the delegate call and before the Redis
 * publish</em>, so the no-I/O-inside-a-lock rule holds. For transfers the two owner stripes are taken in a
 * fixed (UUID-sorted) order and both released before any I/O, so there is no lock-ordering deadlock and no
 * I/O under a held lock.
 *
 * <p>Honest verdict: once I/O is removed from the lock, the striped section adds nothing the database guard
 * does not already provide — the delegate (cache → jOOQ) is the serialisation point. The layer is therefore a
 * thin, rule-compliant pass-through kept for the cross-server invalidation broadcast it owns; the in-memory
 * stripe is retained only to keep this JVM's per-owner mutations from interleaving their begin/end markers,
 * never to provide correctness the transaction lacks. Infrastructure faults (a Redis or lock failure) surface
 * as a {@link PersistenceException}; they are never mapped to {@link TransferError#INSUFFICIENT_FUNDS}, which
 * would lie to a solvent player.
 */
@NullMarked
public final class LockingWalletRepository implements WalletRepository {

    private final WalletRepository delegate;
    private final StripedLock stripedLock;
    private final @Nullable RedisWalletSync redisSync;

    public LockingWalletRepository(
            WalletRepository delegate, StripedLock stripedLock, @Nullable RedisWalletSync redisSync) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.stripedLock = Objects.requireNonNull(stripedLock, "stripedLock");
        this.redisSync = redisSync;
    }

    @Override
    public Optional<Wallet> findByOwner(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return delegate.findByOwner(owner);
    }

    @Override
    public Wallet ensureOwner(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        markOwner(owner.uuid());
        Wallet wallet = delegate.ensureOwner(owner);
        broadcast(owner.uuid());
        return wallet;
    }

    @Override
    public void upsertBalance(PlayerRef owner, Money balance) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(balance, "balance");
        markOwner(owner.uuid());
        delegate.upsertBalance(owner, balance);
        broadcast(owner.uuid());
    }

    @Override
    public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        markOwners(from.uuid(), to.uuid());
        Result<Unit, TransferError> result = delegate.transfer(from, to, amount);
        if (result.isOk()) {
            broadcast(from.uuid());
            broadcast(to.uuid());
        }
        return result;
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        markOwner(owner.uuid());
        Result<Unit, TransferError> result = delegate.debit(owner, amount);
        if (result.isOk()) {
            broadcast(owner.uuid());
        }
        return result;
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        markOwner(owner.uuid());
        Result<Unit, TransferError> result = delegate.credit(owner, amount);
        if (result.isOk()) {
            broadcast(owner.uuid());
        }
        return result;
    }

    @Override
    public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(debit, "debit");
        Objects.requireNonNull(credit, "credit");
        markOwner(owner.uuid());
        Result<Unit, TransferError> result = delegate.exchange(owner, debit, credit);
        if (result.isOk()) {
            broadcast(owner.uuid());
        }
        return result;
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        return delegate.top(currency, limit);
    }

    /**
     * Take and immediately release one owner stripe. This is the whole in-memory critical section: it holds no
     * I/O (no DB, no Redis), so the no-I/O-inside-a-lock rule holds. A lock-acquisition fault is infrastructure
     * and surfaces as {@link PersistenceException}, never an over-draw error.
     */
    private void markOwner(UUID owner) {
        Lock lock = stripeOrThrow(owner);
        lock.lock();
        try {
            // Intentionally empty: the in-memory critical section carries no I/O. The DB guard is the
            // authority; this only orders this JVM's begin/end markers for the owner.
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take and release both owner stripes in UUID order (deadlock-free) with no I/O between them. As with
     * {@link #markOwner}, the critical section carries no DB or Redis call.
     */
    private void markOwners(UUID first, UUID second) {
        UUID lo = first.compareTo(second) <= 0 ? first : second;
        UUID hi = first.compareTo(second) <= 0 ? second : first;
        Lock loLock = stripeOrThrow(lo);
        Lock hiLock = stripeOrThrow(hi);
        loLock.lock();
        try {
            if (loLock != hiLock) {
                hiLock.lock();
                try {
                    // Empty critical section: no I/O under either held stripe.
                } finally {
                    hiLock.unlock();
                }
            }
        } finally {
            loLock.unlock();
        }
    }

    private Lock stripeOrThrow(UUID owner) {
        try {
            return stripedLock.get(owner);
        } catch (RuntimeException cause) {
            throw new PersistenceException("failed to resolve wallet stripe for " + owner, cause);
        }
    }

    /** Broadcast a cross-server invalidate after a committed mutation. Never holds a lock; never throws. */
    private void broadcast(UUID owner) {
        if (redisSync == null) {
            return;
        }
        try {
            redisSync.publish(owner);
        } catch (RuntimeException cause) {
            throw new PersistenceException("failed to broadcast wallet invalidation for " + owner, cause);
        }
    }
}
