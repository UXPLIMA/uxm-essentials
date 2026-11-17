package com.uxplima.uxmessentials.persistence.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.Test;

/** Dropping every cached wallet also drops a read that was already under way when it happened. */
class CachedWalletRepositoryTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final PlayerRef ALEX = new PlayerRef(new UUID(0L, 1L), "Alex");

    @Test
    void aReadUnderWayWhenEverythingWasDroppedIsNotServedAfterwards() throws Exception {
        SlowRows rows = new SlowRows("10");
        CachedWalletRepository cache = new CachedWalletRepository(rows);
        CompletableFuture<BigDecimal> inFlight = CompletableFuture.supplyAsync(() -> balance(cache));
        assertThat(rows.reading.await(10, TimeUnit.SECONDS)).isTrue();

        // A sweep commits a new figure and drops the cache while that read still holds the old one.
        rows.figure = "35";
        cache.invalidateAll();
        rows.release.countDown();

        assertThat(inFlight.get(10, TimeUnit.SECONDS)).isEqualByComparingTo("35");
        assertThat(balance(cache)).isEqualByComparingTo("35");
    }

    private static BigDecimal balance(CachedWalletRepository cache) {
        return cache.findByOwner(ALEX).orElseThrow().balanceOf(COINS).amount();
    }

    /** One owner's coins; the first read stops part way until it is released, after reading the figure. */
    private static final class SlowRows implements WalletRepository {

        private final CountDownLatch reading = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile String figure;

        SlowRows(String figure) {
            this.figure = figure;
        }

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            Wallet read = Wallet.of(owner, Map.of(COINS, Money.of(COINS, new BigDecimal(figure))));
            if (reading.getCount() > 0) {
                reading.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return Optional.of(read);
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            return findByOwner(owner).orElseThrow();
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }
    }
}
