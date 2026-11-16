package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.persistence.economy.CachedWalletRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.network.AllBalancesChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.junit.jupiter.api.Test;

/**
 * A sweep over every account leaves no backend showing a balance from before it: the one that ran it drops its cached
 * wallets, and every other one drops its own on the single frame the sweep sends.
 */
class WalletSyncTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");

    private final Rows rows = new Rows();
    private final CachedWalletRepository cache = new CachedWalletRepository(rows);
    private final Frames frames = new Frames();

    @Test
    void aSweepRunHereShowsItsFiguresAtOnceAndTellsThePeers() {
        rows.put(ALEX, "10");
        assertThat(cached(ALEX)).isEqualByComparingTo("10");
        AccountSweep sweep = WalletSync.sweep(new Sweep(rows, "35", null), cache, frames);

        sweep.creditEvery(Money.of(COINS, new BigDecimal("25")), EconomyReason.ADMIN_GIVEALL, 0L);

        assertThat(cached(ALEX)).isEqualByComparingTo("35");
        assertThat(frames.published).containsExactly(new AllBalancesChanged("lobby", "coins"));
    }

    @Test
    void aSweepThatFailedStillDropsTheCacheAndTellsThePeers() {
        rows.put(ALEX, "10");
        assertThat(cached(ALEX)).isEqualByComparingTo("10");
        AccountSweep sweep =
                WalletSync.sweep(new Sweep(rows, "50", new IllegalStateException("lost at commit")), cache, frames);

        assertThatThrownBy(
                        () -> sweep.resetEvery(Money.of(COINS, new BigDecimal("50")), EconomyReason.ADMIN_RESETALL, 0L))
                .hasMessage("lost at commit");

        assertThat(cached(ALEX)).isEqualByComparingTo("50");
        assertThat(frames.published).containsExactly(new AllBalancesChanged("lobby", "coins"));
    }

    @Test
    void aPeersSweepDropsEveryCachedWallet() {
        rows.put(ALEX, "10");
        assertThat(cached(ALEX)).isEqualByComparingTo("10");
        rows.put(ALEX, "35");

        WalletSync.listener(cache).onRemoteChange(new AllBalancesChanged("survival", "coins"));

        assertThat(cached(ALEX)).isEqualByComparingTo("35");
    }

    private BigDecimal cached(PlayerRef owner) {
        return cache.findByOwner(owner).orElseThrow().balanceOf(COINS).amount();
    }

    /** A sweep that writes {@code figure} into every row, then fails if told to. */
    private record Sweep(
            Rows rows,
            String figure,
            @org.jspecify.annotations.Nullable RuntimeException failure) implements AccountSweep {

        @Override
        public boolean reaches(Currency currency) {
            return true;
        }

        @Override
        public SweepOutcome creditEvery(Money amount, EconomyReason reason, long at) {
            return run();
        }

        @Override
        public SweepOutcome resetEvery(Money balance, EconomyReason reason, long at) {
            return run();
        }

        private SweepOutcome run() {
            rows.held.replaceAll((owner, held) -> new BigDecimal(figure));
            if (failure != null) {
                throw failure;
            }
            return new SweepOutcome(rows.held.size(), 0);
        }
    }

    /** The stored balances in coins, the only thing the cache reads. */
    private static final class Rows implements WalletRepository {

        private final Map<PlayerRef, BigDecimal> held = new HashMap<>();

        void put(PlayerRef owner, String amount) {
            held.put(owner, new BigDecimal(amount));
        }

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            return held.entrySet().stream()
                    .filter(entry -> entry.getKey().uuid().equals(owner.uuid()))
                    .findFirst()
                    .map(entry -> Wallet.of(entry.getKey(), Map.of(COINS, Money.of(COINS, entry.getValue()))));
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            return findByOwner(owner).orElseGet(() -> Wallet.empty(owner));
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {
            held.put(owner, balance.amount());
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

    /** Every frame published, from one backend. */
    private static final class Frames implements BusPublisher {

        private final List<NetworkMessage> published = new ArrayList<>();

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return "lobby";
        }
    }
}
