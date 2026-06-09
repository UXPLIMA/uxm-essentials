package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.coins;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.randomPlayer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.Test;

class LockingWalletRepositoryTest {

    @Test
    void stripedLockProvidesMutualExclusionOnSameOwner() throws Exception {
        StripedLock stripedLock = new StripedLock(16);
        UUID owner = UUID.randomUUID();
        Lock lock1 = stripedLock.get(owner);
        Lock lock2 = stripedLock.get(owner);

        assertThat(lock1).isSameAs(lock2);

        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean secondAcquired = new AtomicBoolean(false);

        lock1.lock();
        try {
            Thread t = new Thread(() -> {
                acquired.countDown();
                try {
                    if (lock2.tryLock(100, TimeUnit.MILLISECONDS)) {
                        try {
                            secondAcquired.set(true);
                        } finally {
                            lock2.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                done.countDown();
            });
            t.start();
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(done.await(200, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(secondAcquired.get()).isFalse();
        } finally {
            lock1.unlock();
        }
    }

    @Test
    void stripedLockAllowsConcurrenyOnDifferentOwners() throws Exception {
        StripedLock stripedLock = new StripedLock(1024); // Large enough to minimize collisions
        UUID owner1 = UUID.randomUUID();
        UUID owner2;
        // Find a second UUID that hashes to a different stripe
        while (true) {
            owner2 = UUID.randomUUID();
            if (stripedLock.get(owner1) != stripedLock.get(owner2)) {
                break;
            }
        }

        Lock lock1 = stripedLock.get(owner1);
        Lock lock2 = stripedLock.get(owner2);

        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean secondAcquired = new AtomicBoolean(false);

        lock1.lock();
        try {
            Thread t = new Thread(() -> {
                acquired.countDown();
                try {
                    if (lock2.tryLock(5, TimeUnit.SECONDS)) {
                        try {
                            secondAcquired.set(true);
                        } finally {
                            lock2.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                done.countDown();
            });
            t.start();
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondAcquired.get()).isTrue();
        } finally {
            lock1.unlock();
        }
    }

    @Test
    void debitDelegatesAndBroadcastsOnSuccess() {
        WalletRepository delegate = mock(WalletRepository.class);
        RedisWalletSync redisSync = mock(RedisWalletSync.class);
        StripedLock stripedLock = new StripedLock(16);

        PlayerRef owner = randomPlayer();
        when(delegate.debit(owner, coins(10))).thenReturn(Result.ok());

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock, redisSync);
        Result<Unit, TransferError> result = repository.debit(owner, coins(10));

        assertThat(result.isOk()).isTrue();
        verify(delegate).debit(owner, coins(10));
        verify(redisSync).publish(owner.uuid());
    }

    @Test
    void doesNotBroadcastWhenMutationFails() {
        WalletRepository delegate = mock(WalletRepository.class);
        RedisWalletSync redisSync = mock(RedisWalletSync.class);
        StripedLock stripedLock = new StripedLock(16);

        PlayerRef owner = randomPlayer();
        when(delegate.debit(owner, coins(10))).thenReturn(Result.err(TransferError.INSUFFICIENT_FUNDS));

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock, redisSync);
        Result<Unit, TransferError> result = repository.debit(owner, coins(10));

        assertThat(result.isErr()).isTrue();
        verify(delegate).debit(owner, coins(10));
        verify(redisSync, never()).publish(any(UUID.class));
    }

    @Test
    void transferBroadcastsBothOwnersOnSuccess() {
        WalletRepository delegate = mock(WalletRepository.class);
        RedisWalletSync redisSync = mock(RedisWalletSync.class);
        StripedLock stripedLock = new StripedLock(16);

        PlayerRef from = randomPlayer();
        PlayerRef to = randomPlayer();
        when(delegate.transfer(from, to, coins(50))).thenReturn(Result.ok());

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock, redisSync);
        Result<Unit, TransferError> result = repository.transfer(from, to, coins(50));

        assertThat(result.isOk()).isTrue();
        verify(delegate).transfer(from, to, coins(50));
        verify(redisSync, times(1)).publish(from.uuid());
        verify(redisSync, times(1)).publish(to.uuid());
    }

    @Test
    void worksWithoutRedisSync() {
        WalletRepository delegate = mock(WalletRepository.class);
        StripedLock stripedLock = new StripedLock(16);

        PlayerRef owner = randomPlayer();
        when(delegate.credit(owner, coins(5))).thenReturn(Result.ok());

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock, null);
        Result<Unit, TransferError> result = repository.credit(owner, coins(5));

        assertThat(result.isOk()).isTrue();
        verify(delegate).credit(owner, coins(5));
    }

    @Test
    void infraFailureThrowsPersistenceExceptionNotInsufficientFunds() {
        WalletRepository delegate = mock(WalletRepository.class);
        RedisWalletSync redisSync = mock(RedisWalletSync.class);
        StripedLock stripedLock = new StripedLock(16);

        PlayerRef owner = randomPlayer();
        when(delegate.debit(owner, coins(10))).thenReturn(Result.ok());
        // A Redis outage on the post-commit broadcast is infrastructure, not an over-draw: it must surface as
        // a PersistenceException, never a lie that the solvent player had INSUFFICIENT_FUNDS.
        doThrow(new RuntimeException("redis down")).when(redisSync).publish(owner.uuid());

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock, redisSync);

        assertThatThrownBy(() -> repository.debit(owner, coins(10)))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining(owner.uuid().toString());
    }

    @Test
    void noLockIsHeldWhileTheDelegateRuns() {
        // The stripe for this owner must be free while the delegate executes, proving no lock spans the I/O.
        StripedLock stripedLock = new StripedLock(16);
        PlayerRef owner = randomPlayer();
        Lock ownerStripe = stripedLock.get(owner.uuid());

        AtomicReference<Boolean> stripeFreeDuringDelegate = new AtomicReference<>();
        WalletRepository delegate = mock(WalletRepository.class);
        doAnswer(invocation -> {
                    boolean acquired = ownerStripe.tryLock();
                    stripeFreeDuringDelegate.set(acquired);
                    if (acquired) {
                        ownerStripe.unlock();
                    }
                    return Result.ok();
                })
                .when(delegate)
                .debit(owner, coins(7));

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock, null);
        repository.debit(owner, coins(7));

        assertThat(stripeFreeDuringDelegate.get()).isTrue();
    }

    @Test
    void stripeResolutionFailureSurfacesAsPersistenceException() {
        WalletRepository delegate = mock(WalletRepository.class);
        StripedLock stripedLock = mock(StripedLock.class);
        PlayerRef owner = randomPlayer();
        when(stripedLock.get(owner.uuid())).thenThrow(new IllegalStateException("stripe blew up"));

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock, null);

        assertThatThrownBy(() -> repository.debit(owner, coins(1))).isInstanceOf(PersistenceException.class);
        verify(delegate, never()).debit(any(), any());
    }
}
