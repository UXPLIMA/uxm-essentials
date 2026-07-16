package com.uxplima.uxmessentials.persistence.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.TradeEscrow;
import com.uxplima.uxmessentials.trade.application.TradeEscrowPhase;
import com.uxplima.uxmessentials.trade.application.port.TradeEscrowStore;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqTradeEscrowStore} against the default embedded SQLite backend with the Flyway
 * V1-V75 migrations applied. It proves the escrow contract over the {@code trade_escrow} table: a stake round-trips
 * every field (items, item count, multi-currency money to scale, servers, phase, timestamp); the guarded transitions
 * fire exactly once ({@code commitBoth} only when both sides are held, {@code claim} only from committed,
 * {@code beginRefund} only from held); and each transition is a no-op on the second call — the exactly-once guarantee
 * the two-phase commit relies on.
 */
class JooqTradeEscrowStoreTest {

    private static final TradeId TRADE = TradeId.newId();
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    private Persistence persistence;
    private TradeEscrowStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqTradeEscrowStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void anEscrowedStakeRoundTripsEveryField() {
        store.escrow(TradeEscrow.held(
                TRADE,
                ALICE,
                "survival-1",
                BOB,
                "lobby-2",
                "c023Base64Items==",
                7,
                Map.of("coins", new BigDecimal("250.50"), "gems", new BigDecimal("3")),
                Instant.ofEpochMilli(1_700_000_000_000L)));

        TradeEscrow found = store.find(TRADE, ALICE.uuid()).orElseThrow();
        assertThat(found.owner()).isEqualTo(ALICE);
        assertThat(found.ownerServer()).isEqualTo("survival-1");
        assertThat(found.counterparty()).isEqualTo(BOB);
        assertThat(found.counterpartyServer()).isEqualTo("lobby-2");
        assertThat(found.itemData()).isEqualTo("c023Base64Items==");
        assertThat(found.itemCount()).isEqualTo(7);
        assertThat(found.phase()).isEqualTo(TradeEscrowPhase.HELD);
        assertThat(found.createdAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(found.money()).containsOnlyKeys("coins", "gems");
        assertThat(found.money().get("coins")).isEqualByComparingTo("250.50");
        assertThat(found.money().get("gems")).isEqualByComparingTo("3");
    }

    @Test
    void aMoneyOnlyStakeRoundTripsBlankItems() {
        store.escrow(TradeEscrow.held(
                TRADE, ALICE, "s1", BOB, "s2", "", 0, Map.of("coins", new BigDecimal("10")), Instant.EPOCH));

        TradeEscrow found = store.find(TRADE, ALICE.uuid()).orElseThrow();
        assertThat(found.hasItems()).isFalse();
        assertThat(found.itemData()).isEmpty();
    }

    @Test
    void commitBothFlipsOnlyWhenBothSidesAreHeldAndIsIdempotent() {
        hold(ALICE, BOB);
        assertThat(store.commitBoth(TRADE, ALICE.uuid(), BOB.uuid()))
                .as("only one side held")
                .isFalse();

        hold(BOB, ALICE);
        assertThat(store.commitBoth(TRADE, ALICE.uuid(), BOB.uuid())).isTrue();
        assertThat(store.find(TRADE, ALICE.uuid()).orElseThrow().phase()).isEqualTo(TradeEscrowPhase.COMMITTED);
        assertThat(store.find(TRADE, BOB.uuid()).orElseThrow().phase()).isEqualTo(TradeEscrowPhase.COMMITTED);
        assertThat(store.commitBoth(TRADE, ALICE.uuid(), BOB.uuid()))
                .as("already committed")
                .isFalse();
    }

    @Test
    void claimAdvancesACommittedRowExactlyOnce() {
        hold(ALICE, BOB);
        hold(BOB, ALICE);
        store.commitBoth(TRADE, ALICE.uuid(), BOB.uuid());

        assertThat(store.claim(TRADE, ALICE.uuid())).isTrue();
        assertThat(store.find(TRADE, ALICE.uuid()).orElseThrow().phase()).isEqualTo(TradeEscrowPhase.CLAIMED);
        assertThat(store.claim(TRADE, ALICE.uuid()))
                .as("a committed row is claimed once")
                .isFalse();
    }

    @Test
    void beginRefundDeletesOnlyAHeldRow() {
        hold(ALICE, BOB);
        assertThat(store.beginRefund(TRADE, ALICE.uuid())).isTrue();
        assertThat(store.find(TRADE, ALICE.uuid())).isEmpty();
        assertThat(store.beginRefund(TRADE, ALICE.uuid()))
                .as("nothing left to refund")
                .isFalse();
    }

    @Test
    void aCommittedRowCannotBeRefunded() {
        hold(ALICE, BOB);
        hold(BOB, ALICE);
        store.commitBoth(TRADE, ALICE.uuid(), BOB.uuid());

        assertThat(store.beginRefund(TRADE, ALICE.uuid())).isFalse();
        assertThat(store.find(TRADE, ALICE.uuid())).isPresent();
    }

    @Test
    void findByOwnerReturnsEveryStakeThePlayerHolds() {
        hold(ALICE, BOB);
        assertThat(store.findByOwner(ALICE.uuid())).hasSize(1);
        assertThat(store.findByOwner(BOB.uuid())).isEmpty();
    }

    private void hold(PlayerRef owner, PlayerRef counterparty) {
        store.escrow(TradeEscrow.held(
                TRADE, owner, "s1", counterparty, "s2", "x", 1, Map.of("coins", new BigDecimal("5")), Instant.EPOCH));
    }

    /** A config that selects the embedded SQLite backend with every default. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
