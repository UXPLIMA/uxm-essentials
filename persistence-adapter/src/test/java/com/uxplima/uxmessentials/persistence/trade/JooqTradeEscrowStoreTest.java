package com.uxplima.uxmessentials.persistence.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.TradeEscrow;
import com.uxplima.uxmessentials.trade.application.TradeEscrowPhase;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Trade stakes through the real migrations: both sides commit together or neither does, a committed stake is claimed
 * once, and a refund only takes back a stake that never committed.
 */
@EveryBackend
class JooqTradeEscrowStoreTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Sam");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private final TradeId trade = TradeId.fromString("00000000-0000-0000-0000-00000000f00d");
    private JooqTradeEscrowStore store;

    @BeforeEach
    void open(TestDatabase database) {
        store = new JooqTradeEscrowStore(database.dsl());
        store.escrow(stake(ALEX, SAM, Map.of("coins", new BigDecimal("12.50"))));
        store.escrow(stake(SAM, ALEX, Map.of()));
    }

    @Test
    void aStakeIsReadBackWithItsItemsAndMoney() {
        TradeEscrow alex = store.find(trade, ALEX.uuid()).orElseThrow();

        assertThat(alex.itemData()).isEqualTo("aXRlbXM=");
        assertThat(alex.itemCount()).isEqualTo(3);
        assertThat(alex.money().get("coins")).isEqualByComparingTo("12.50");
        assertThat(alex.phase()).isEqualTo(TradeEscrowPhase.HELD);
        assertThat(store.findByOwner(SAM.uuid()))
                .extracting(TradeEscrow::tradeId)
                .containsExactly(trade);
    }

    @Test
    void bothSidesCommitTogetherAndEachClaimsItsStakeOnce() {
        assertThat(store.commitBoth(trade, ALEX.uuid(), SAM.uuid())).isTrue();

        assertThat(store.claim(trade, ALEX.uuid())).isTrue();
        assertThat(store.claim(trade, ALEX.uuid())).isFalse();
        assertThat(store.beginRefund(trade, SAM.uuid())).isFalse();
        assertThat(store.find(trade, SAM.uuid()).orElseThrow().phase()).isEqualTo(TradeEscrowPhase.COMMITTED);
    }

    @Test
    void aSideRefundedFirstLeavesNoCommitAndTheOtherSideRefundable() {
        assertThat(store.beginRefund(trade, SAM.uuid())).isTrue();

        assertThat(store.commitBoth(trade, ALEX.uuid(), SAM.uuid())).isFalse();

        assertThat(store.find(trade, ALEX.uuid()).orElseThrow().phase()).isEqualTo(TradeEscrowPhase.HELD);
        assertThat(store.beginRefund(trade, ALEX.uuid())).isTrue();
    }

    @Test
    void aCommitAndARefundRacingForOneTradeNeverBothLand() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Boolean> commit = CompletableFuture.supplyAsync(() -> {
            await(start);
            return store.commitBoth(trade, ALEX.uuid(), SAM.uuid());
        });
        CompletableFuture<Boolean> refund = CompletableFuture.supplyAsync(() -> {
            await(start);
            return store.beginRefund(trade, SAM.uuid());
        });
        start.countDown();

        boolean committed = commit.get(30, TimeUnit.SECONDS);
        boolean refunded = refund.get(30, TimeUnit.SECONDS);

        assertThat(committed && refunded).isFalse();
        assertThat(committed || refunded).isTrue();
        assertThat(store.find(trade, ALEX.uuid()).orElseThrow().phase())
                .isEqualTo(committed ? TradeEscrowPhase.COMMITTED : TradeEscrowPhase.HELD);
    }

    @Test
    void aClearedStakeIsGone() {
        store.clear(trade, ALEX.uuid());

        assertThat(store.find(trade, ALEX.uuid())).isEmpty();
        assertThat(store.find(trade, SAM.uuid())).isPresent();
    }

    private TradeEscrow stake(PlayerRef owner, PlayerRef counterparty, Map<String, BigDecimal> money) {
        return TradeEscrow.held(trade, owner, "survival", counterparty, "lobby", "aXRlbXM=", 3, money, NOW);
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
