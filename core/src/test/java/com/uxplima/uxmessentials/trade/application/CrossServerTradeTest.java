package com.uxplima.uxmessentials.trade.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.application.port.TradeEscrowStore;
import com.uxplima.uxmessentials.trade.application.port.TradeItemDelivery;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.junit.jupiter.api.Test;

/**
 * Alex on the lobby backend trades with Sam on the survival backend through the shared escrow table. The test carries
 * each bus frame by hand, so it can repeat one, drop one, or deliver it while the addressed player is away, and checks
 * that every coin and item ends up in exactly one place.
 */
class CrossServerTradeTest {

    private static final String ALEX_ITEMS = "alex:diamond x3";
    private static final String SAM_ITEMS = "sam:elytra x1";

    private final MutableClock clock = new MutableClock(NOW);
    private final Escrows escrows = new Escrows();
    private final TradeWallets wallets = new TradeWallets();
    private final Backend lobby = new Backend("lobby");
    private final Backend survival = new Backend("survival");
    private final TradeId id = TradeId.newId();

    CrossServerTradeTest() {
        wallets.set(ALEX, "coins", "100").set(SAM, "coins", "20").set(SAM, "gems", "5");
        lobby.online.add(ALEX.uuid());
        survival.online.add(SAM.uuid());
    }

    private boolean alexEscrows(Map<String, BigDecimal> money) {
        return lobby.trade.escrow(id, ALEX, "survival", SAM, ALEX_ITEMS, 3, money);
    }

    private boolean samEscrows(String itemData, Map<String, BigDecimal> money) {
        return survival.trade.escrow(id, SAM, "lobby", ALEX, itemData, itemData.isBlank() ? 0 : 1, money);
    }

    private static Map<String, BigDecimal> coins(String amount) {
        return Map.of("coins", new BigDecimal(amount));
    }

    @Test
    void bothSidesEscrowedSwapEverythingExactlyOnceAndLeaveNoRows() {
        alexEscrows(coins("40"));
        samEscrows(SAM_ITEMS, Map.of("gems", new BigDecimal("5")));

        lobby.receive(survival.only(TradeSignalType.COMMIT));

        assertThat(survival.delivered).containsExactly(new Delivery(SAM, ALEX_ITEMS));
        assertThat(lobby.delivered).containsExactly(new Delivery(ALEX, SAM_ITEMS));
        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("60");
        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("60");
        assertThat(wallets.balance(ALEX, "gems")).isEqualByComparingTo("5");
        assertThat(wallets.balance(SAM, "gems")).isEqualByComparingTo("0");
        assertThat(escrows.rows).isEmpty();
    }

    @Test
    void repeatedSignalsAndRejoinsAfterTheSwapDeliverNothingMore() {
        alexEscrows(coins("40"));
        samEscrows(SAM_ITEMS, Map.of());
        TradeSignal commit = survival.only(TradeSignalType.COMMIT);
        TradeSignal ready = lobby.only(TradeSignalType.READY);
        lobby.receive(commit);

        lobby.receive(commit);
        survival.receive(ready);
        lobby.trade.reconcile(ALEX.uuid());
        survival.trade.reconcile(SAM.uuid());

        assertThat(survival.delivered).hasSize(1);
        assertThat(lobby.delivered).hasSize(1);
        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("60");
        assertThat(wallets.total("coins")).isEqualByComparingTo("120");
    }

    @Test
    void aSideThatCannotCoverEveryCurrencyEscrowsNothingAndGetsTheCoveredOneBack() {
        Map<String, BigDecimal> money = new LinkedHashMap<>();
        money.put("coins", new BigDecimal("10"));
        money.put("gems", new BigDecimal("6"));

        assertThat(samEscrows(SAM_ITEMS, money)).isFalse();

        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("20");
        assertThat(wallets.balance(SAM, "gems")).isEqualByComparingTo("5");
        assertThat(escrows.rows).isEmpty();
        assertThat(survival.sent).isEmpty();
    }

    @Test
    void backingOutBeforeThePeerEscrowsRefundsOnceAndTellsThePeer() {
        alexEscrows(coins("40"));
        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("60");

        lobby.trade.cancel(id, ALEX.uuid());
        lobby.trade.cancel(id, ALEX.uuid());

        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("100");
        assertThat(lobby.delivered).containsExactly(new Delivery(ALEX, ALEX_ITEMS));
        assertThat(lobby.only(TradeSignalType.ABORT).to()).isEqualTo(SAM);
        assertThat(escrows.rows).isEmpty();
    }

    @Test
    void aPeerThatEscrowedWhileTheAbortWasInFlightIsRefundedWhenItArrives() {
        alexEscrows(coins("40"));
        lobby.trade.cancel(id, ALEX.uuid());
        samEscrows(SAM_ITEMS, coins("15"));

        TradeSignal abort = lobby.only(TradeSignalType.ABORT);
        survival.receive(abort);
        survival.receive(abort);

        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("20");
        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("100");
        assertThat(survival.delivered).containsExactly(new Delivery(SAM, SAM_ITEMS));
        assertThat(lobby.delivered).containsExactly(new Delivery(ALEX, ALEX_ITEMS));
        assertThat(escrows.rows).isEmpty();
    }

    @Test
    void backingOutAfterTheCommitPointReturnsNothingAndTheSwapStillCompletes() {
        alexEscrows(coins("40"));
        samEscrows(SAM_ITEMS, Map.of());

        lobby.trade.cancel(id, ALEX.uuid());
        lobby.receive(survival.only(TradeSignalType.COMMIT));

        assertThat(lobby.delivered).containsExactly(new Delivery(ALEX, SAM_ITEMS));
        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("60");
        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("60");
        assertThat(lobby.sent).extracting(TradeSignal::type).doesNotContain(TradeSignalType.ABORT);
    }

    @Test
    void itemsForARecipientWhoLeftWaitForTheirNextJoin() {
        alexEscrows(coins("40"));
        samEscrows(SAM_ITEMS, coins("5"));
        lobby.online.remove(ALEX.uuid());

        lobby.receive(survival.only(TradeSignalType.COMMIT));

        assertThat(lobby.delivered).isEmpty();
        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("60");

        lobby.online.add(ALEX.uuid());
        lobby.trade.reconcile(ALEX.uuid());
        lobby.trade.reconcile(ALEX.uuid());

        assertThat(lobby.delivered).containsExactly(new Delivery(ALEX, SAM_ITEMS));
        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("65");
        assertThat(escrows.rows).isEmpty();
    }

    @Test
    void moneyOnlyGoodsReachARecipientWhoIsAway() {
        alexEscrows(coins("40"));
        samEscrows("", coins("5"));
        lobby.online.remove(ALEX.uuid());

        lobby.receive(survival.only(TradeSignalType.COMMIT));

        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("65");
        assertThat(escrows.rows).isEmpty();
    }

    @Test
    void aStakeWhosePeerNeverEscrowedIsRefundedOnTheOwnersNextJoin() {
        alexEscrows(coins("40"));
        lobby.online.remove(ALEX.uuid());
        // The owner's server went down before the window closed, so no cancel ran.

        lobby.online.add(ALEX.uuid());
        lobby.trade.reconcile(ALEX.uuid());

        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("100");
        assertThat(lobby.delivered).containsExactly(new Delivery(ALEX, ALEX_ITEMS));
        assertThat(escrows.rows).isEmpty();
    }

    @Test
    void anAbortForAnOwnerWhoIsAwayKeepsTheirItemsUntilTheyReturn() {
        alexEscrows(coins("40"));
        lobby.trade.cancel(id, ALEX.uuid());
        samEscrows(SAM_ITEMS, coins("15"));
        survival.online.remove(SAM.uuid());

        survival.receive(lobby.only(TradeSignalType.ABORT));

        assertThat(survival.delivered).isEmpty();
        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("5");

        survival.online.add(SAM.uuid());
        survival.trade.reconcile(SAM.uuid());

        assertThat(survival.delivered).containsExactly(new Delivery(SAM, SAM_ITEMS));
        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("20");
        assertThat(escrows.rows).isEmpty();
    }

    private record Delivery(PlayerRef to, String itemData) {}

    /** One backend: its own bus, its own online players and the items it handed to them. */
    private final class Backend implements TradeBus, TradeItemDelivery {

        private final String server;
        private final List<TradeSignal> sent = new ArrayList<>();
        private final List<Delivery> delivered = new ArrayList<>();
        private final Set<UUID> online = new HashSet<>();
        private final CrossServerTrade trade;

        Backend(String server) {
            this.server = server;
            this.trade = new CrossServerTrade(escrows, wallets, this, this, clock, new QuietLog());
        }

        void receive(TradeSignal signal) {
            trade.onSignal(signal);
        }

        TradeSignal only(TradeSignalType type) {
            List<TradeSignal> matching =
                    sent.stream().filter(signal -> signal.type() == type).toList();
            assertThat(matching).hasSize(1);
            return matching.get(0);
        }

        @Override
        public void send(TradeSignal signal) {
            sent.add(signal);
        }

        @Override
        public void subscribe(Consumer<TradeSignal> handler) {}

        @Override
        public String localServer() {
            return server;
        }

        @Override
        public boolean healthy() {
            return true;
        }

        @Override
        public boolean isOnline(PlayerRef player) {
            return online.contains(player.uuid());
        }

        @Override
        public void deliver(PlayerRef player, String itemData) {
            if (!itemData.isBlank()) {
                delivered.add(new Delivery(player, itemData));
            }
        }
    }

    /** The shared escrow table, every phase change a guarded single-row (or, for the commit, two-row) update. */
    private static final class Escrows implements TradeEscrowStore {

        private final Map<String, TradeEscrow> rows = new HashMap<>();

        private static String key(TradeId id, UUID owner) {
            return id + "/" + owner;
        }

        @Override
        public void escrow(TradeEscrow escrow) {
            rows.put(key(escrow.tradeId(), escrow.owner().uuid()), escrow);
        }

        @Override
        public Optional<TradeEscrow> find(TradeId tradeId, UUID owner) {
            return Optional.ofNullable(rows.get(key(tradeId, owner)));
        }

        @Override
        public List<TradeEscrow> findByOwner(UUID owner) {
            return rows.values().stream()
                    .filter(row -> row.owner().uuid().equals(owner))
                    .toList();
        }

        @Override
        public boolean commitBoth(TradeId tradeId, UUID a, UUID b) {
            TradeEscrow first = rows.get(key(tradeId, a));
            TradeEscrow second = rows.get(key(tradeId, b));
            if (first == null
                    || second == null
                    || first.phase() != TradeEscrowPhase.HELD
                    || second.phase() != TradeEscrowPhase.HELD) {
                return false;
            }
            rows.put(key(tradeId, a), first.withPhase(TradeEscrowPhase.COMMITTED));
            rows.put(key(tradeId, b), second.withPhase(TradeEscrowPhase.COMMITTED));
            return true;
        }

        @Override
        public boolean claim(TradeId tradeId, UUID owner) {
            TradeEscrow row = rows.get(key(tradeId, owner));
            if (row == null || row.phase() != TradeEscrowPhase.COMMITTED) {
                return false;
            }
            rows.put(key(tradeId, owner), row.withPhase(TradeEscrowPhase.CLAIMED));
            return true;
        }

        @Override
        public boolean beginRefund(TradeId tradeId, UUID owner) {
            TradeEscrow row = rows.get(key(tradeId, owner));
            if (row == null || row.phase() != TradeEscrowPhase.HELD) {
                return false;
            }
            rows.remove(key(tradeId, owner));
            return true;
        }

        @Override
        public void clear(TradeId tradeId, UUID owner) {
            rows.remove(key(tradeId, owner));
        }
    }
}
