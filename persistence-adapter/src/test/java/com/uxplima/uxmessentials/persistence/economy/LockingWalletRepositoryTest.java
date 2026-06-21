package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.coins;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.randomPlayer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LockingWalletRepositoryTest {

    private final ExecutorService contention = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdownContentionPool() throws InterruptedException {
        contention.shutdownNow();
        contention.awaitTermination(5, TimeUnit.SECONDS);
    }

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
            contention.execute(() -> {
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
            contention.execute(() -> {
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
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondAcquired.get()).isTrue();
        } finally {
            lock1.unlock();
        }
    }

    @Test
    void debitDelegatesOnSuccess() {
        WalletRepository delegate = mock(WalletRepository.class);
        StripedLock stripedLock = new StripedLock(16);

        PlayerRef owner = randomPlayer();
        when(delegate.debit(owner, coins(10))).thenReturn(Result.ok());

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock);
        Result<Unit, TransferError> result = repository.debit(owner, coins(10));

        assertThat(result.isOk()).isTrue();
        verify(delegate).debit(owner, coins(10));
    }

    @Test
    void transferDelegatesBothOwnersOnSuccess() {
        WalletRepository delegate = mock(WalletRepository.class);
        StripedLock stripedLock = new StripedLock(16);

        PlayerRef from = randomPlayer();
        PlayerRef to = randomPlayer();
        when(delegate.transfer(from, to, coins(50))).thenReturn(Result.ok());

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock);
        Result<Unit, TransferError> result = repository.transfer(from, to, coins(50));

        assertThat(result.isOk()).isTrue();
        verify(delegate).transfer(from, to, coins(50));
    }

    @Test
    void creditDelegates() {
        WalletRepository delegate = mock(WalletRepository.class);
        StripedLock stripedLock = new StripedLock(16);

        PlayerRef owner = randomPlayer();
        when(delegate.credit(owner, coins(5))).thenReturn(Result.ok());

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock);
        Result<Unit, TransferError> result = repository.credit(owner, coins(5));

        assertThat(result.isOk()).isTrue();
        verify(delegate).credit(owner, coins(5));
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

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock);
        repository.debit(owner, coins(7));

        assertThat(stripeFreeDuringDelegate.get()).isTrue();
    }

    @Test
    void stripeResolutionFailureSurfacesAsPersistenceException() {
        WalletRepository delegate = mock(WalletRepository.class);
        StripedLock stripedLock = mock(StripedLock.class);
        PlayerRef owner = randomPlayer();
        when(stripedLock.get(owner.uuid())).thenThrow(new IllegalStateException("stripe blew up"));

        LockingWalletRepository repository = new LockingWalletRepository(delegate, stripedLock);

        assertThatThrownBy(() -> repository.debit(owner, coins(1))).isInstanceOf(PersistenceException.class);
        verify(delegate, never()).debit(any(), any());
    }
}
