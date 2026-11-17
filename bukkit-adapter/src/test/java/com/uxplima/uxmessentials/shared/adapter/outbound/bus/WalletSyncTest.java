package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepMode;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.persistence.economy.CachedWalletRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.network.AllBalancesChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A sweep over every account leaves no backend showing a balance from before it: the one running it drops the cached
 * wallets each chunk changed, and every other one drops its own on the frames the sweep sends now and then.
 */
class WalletSyncTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final AllBalancesChanged COINS_SWEPT = new AllBalancesChanged("lobby", "coins");

    private final Rows rows = new Rows();
    private final CachedWalletRepository cache = new CachedWalletRepository(rows);
    private final Frames frames = new Frames();
    private final AtomicLong nanos = new AtomicLong();
    private final Chunks chunks = new Chunks(rows);
    private final AccountSweep sweep = new WalletSync.AnnouncingSweep(chunks, cache, frames, nanos::get);

    @Test
    void eachChunkShowsItsFiguresHereAtOnceAndPeersHearNowAndThenAndAtTheEnd() {
        rows.put(ALEX, "10");
        rows.put(SAM, "20");
        assertThat(cached(ALEX)).isEqualByComparingTo("10");
        assertThat(cached(SAM)).isEqualByComparingTo("20");
        AccountSweep.Sweep running = open();

        sweep.advance(running);
        assertThat(cached(ALEX)).isEqualByComparingTo("35");
        assertThat(cached(SAM)).isEqualByComparingTo("20");
        assertThat(frames.published).containsExactly(COINS_SWEPT);

        nanos.addAndGet(WalletSync.ANNOUNCE_EVERY.toNanos() / 2);
        sweep.advance(running);
        assertThat(cached(SAM)).isEqualByComparingTo("45");
        assertThat(frames.published).hasSize(1);

        sweep.advance(running);
        assertThat(frames.published).containsExactly(COINS_SWEPT, COINS_SWEPT);
    }

    @Test
    void aChunkThatFailedStillDropsTheCacheAndTellsThePeers() {
        rows.put(ALEX, "10");
        assertThat(cached(ALEX)).isEqualByComparingTo("10");
        chunks.failAfterWriting(new IllegalStateException("lost at commit"));

        assertThatThrownBy(() -> sweep.advance(open())).hasMessage("lost at commit");

        assertThat(cached(ALEX)).isEqualByComparingTo("35");
        assertThat(frames.published).containsExactly(COINS_SWEPT);
    }

    @Test
    void aPeersSweepDropsEveryCachedWallet() {
        rows.put(ALEX, "10");
        assertThat(cached(ALEX)).isEqualByComparingTo("10");
        rows.put(ALEX, "35");

        WalletSync.listener(cache).onRemoteChange(new AllBalancesChanged("survival", "coins"));

        assertThat(cached(ALEX)).isEqualByComparingTo("35");
    }

    private AccountSweep.Sweep open() {
        return ((AccountSweep.Opened) sweep.open(
                        SweepKind.CREDIT,
                        SweepScope.PLAYERS,
                        SweepMode.LEDGER,
                        Money.of(COINS, new BigDecimal("25")),
                        EconomyReason.ADMIN_GIVEALL,
                        ALEX,
                        0L))
                .sweep();
    }

    private BigDecimal cached(PlayerRef owner) {
        return cache.findByOwner(owner).orElseThrow().balanceOf(COINS).amount();
    }

    /** A sweep that credits 25 to one stored account per chunk, in the order they were put, then finishes. */
    private static final class Chunks implements AccountSweep {

        private final Rows rows;
        private int next;
        private @Nullable RuntimeException failure;

        Chunks(Rows rows) {
            this.rows = rows;
        }

        void failAfterWriting(RuntimeException thrown) {
            failure = thrown;
        }

        @Override
        public Opening open(
                SweepKind kind,
                SweepScope scope,
                SweepMode mode,
                Money amount,
                EconomyReason reason,
                PlayerRef actor,
                long at) {
            return new Opened(new Sweep("sweep-1", kind, scope, mode, amount, reason, actor, at));
        }

        @Override
        public void settle(Sweep sweep, SweepOutcome done) {}

        @Override
        public Step advance(Sweep sweep) {
            List<PlayerRef> owners = new ArrayList<>(rows.held.keySet());
            if (next >= owners.size()) {
                return new Step(new SweepOutcome(next, 0, 0), List.of(), Progress.FINISHED);
            }
            PlayerRef owner = owners.get(next++);
            rows.held.merge(owner, new BigDecimal("25"), BigDecimal::add);
            RuntimeException thrown = failure;
            if (thrown != null) {
                throw thrown;
            }
            return new Step(new SweepOutcome(next, 0, 0), List.of(owner.uuid()), Progress.MORE);
        }

        @Override
        public void pause(Sweep sweep, String error) {}

        @Override
        public List<Sweep> resumable() {
            return List.of();
        }

        @Override
        public List<SweepStatus> unfinished() {
            return List.of();
        }

        @Override
        public Optional<SweepStatus> cancel(String id, long at) {
            return Optional.empty();
        }
    }

    /** The stored balances in coins, the only thing the cache reads, in the order they were first put. */
    private static final class Rows implements WalletRepository {

        private final Map<PlayerRef, BigDecimal> held = new java.util.LinkedHashMap<>();

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
