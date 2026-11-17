package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.ItemDeliveryQueue;
import com.uxplima.uxmessentials.economy.application.port.ItemPurse;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Denomination;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.event.WalletCredited;
import com.uxplima.uxmessentials.economy.domain.event.WalletDebited;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The deliveries a sweep queued reach a player once, what does not fit waits for their next join instead of falling on
 * the ground, a reset brings the balance to the starting figure before any give that came after it, and a hand-over
 * whose finish could not be stored is never handed over again.
 */
class DeliverSweptItemsTest {

    private static final Currency GOLD = Currency.builder(CurrencyId.of("gold"))
            .physical(true)
            .denominations(List.of(new Denomination(BigDecimal.ONE, "GOLD_NUGGET", "gold nugget", null)))
            .starting(new BigDecimal("10"))
            .build();
    private static final PlayerRef ALEX = new PlayerRef(new UUID(0L, 1L), "Alex");

    private final Queue queue = new Queue();
    private final Purse purse = new Purse();
    private final List<String> ledger = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final Map<UUID, Boolean> online = new HashMap<>(Map.of(ALEX.uuid(), true));
    private final QueuedScheduler timers = new QueuedScheduler();
    private boolean deferred = true;
    private final RecordingEvents events = new RecordingEvents();
    private final RecordingLogger log = new RecordingLogger();
    private final List<PlayerRef> creditsPaid = new ArrayList<>();
    private final DeliverSweptItems deliver = new DeliverSweptItems(
            queue,
            currency -> deferred ? Optional.<ItemPurse>of(purse) : Optional.empty(),
            CurrencyRegistry.of(List.of(GOLD), GOLD.id()),
            new Ledger(ledger),
            new EconomyNotifier(
                    (PlayerRef viewer, MessageKey key, Map<String, String> values) -> {
                        lines.add(key.key());
                        return key.key();
                    },
                    (viewer, text) -> {}),
            events,
            creditsPaid::add,
            new Online(online),
            timers,
            log,
            CLOCK);

    @Test
    void aGiveFillsTheInventoryAndKeepsTheRestForTheNextJoin() {
        purse.room = new BigDecimal("15");
        queue.add("d1", ItemDeliveryQueue.Kind.GIVE, "25");

        deliver.toOnline(List.of(ALEX));

        assertThat(purse.held).isEqualByComparingTo("15");
        assertThat(ledger).containsExactly("CREDIT:15:ADMIN_GIVEALL");
        assertThat(lines)
                .containsExactly(
                        EconomyMessageKey.ECO_ITEMS_DELIVERED.key(), EconomyMessageKey.ECO_ITEMS_WAITING.key());
        // The rest is held back: the next pass over the players online leaves it alone.
        deliver.toOnline(List.of(ALEX));
        assertThat(purse.held).isEqualByComparingTo("15");

        purse.room = new BigDecimal("100");
        deliver.onJoin(ALEX);

        assertThat(purse.held).isEqualByComparingTo("25");
        assertThat(queue.rows).isEmpty();
        assertThat(ledger).containsExactly("CREDIT:15:ADMIN_GIVEALL", "CREDIT:10:ADMIN_GIVEALL");
    }

    @Test
    void aResetTakesTheHeldAmountDownToTheStartingFigure() {
        purse.held = new BigDecimal("40");
        queue.add("d1", ItemDeliveryQueue.Kind.RESET, "10");

        deliver.onJoin(ALEX);

        assertThat(purse.held).isEqualByComparingTo("10");
        assertThat(queue.rows).isEmpty();
        assertThat(ledger).containsExactly("DEBIT:30:ADMIN_RESETALL");
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ITEMS_RESET.key());
    }

    @Test
    void aResetThatNeedsMoreThanFitsKeepsTheShortfallUnderItsOwnReason() {
        purse.room = new BigDecimal("4");
        queue.add("d1", ItemDeliveryQueue.Kind.RESET, "10");

        deliver.onJoin(ALEX);

        assertThat(purse.held).isEqualByComparingTo("4");
        assertThat(queue.rows.values()).singleElement().satisfies(row -> {
            assertThat(row.delivery.kind()).isEqualTo(ItemDeliveryQueue.Kind.GIVE);
            assertThat(row.delivery.amount()).isEqualByComparingTo("6");
            assertThat(row.delivery.reason()).isEqualTo(EconomyReason.ADMIN_RESETALL);
            assertThat(row.heldBack).isTrue();
        });
    }

    @Test
    void aResetIsAppliedBeforeTheGivesBesideItWhateverOrderTheyWereRead() {
        purse.held = new BigDecimal("40");
        queue.add("give", ItemDeliveryQueue.Kind.GIVE, "5");
        queue.add("reset", ItemDeliveryQueue.Kind.RESET, "10");

        deliver.onJoin(ALEX);

        assertThat(purse.held).isEqualByComparingTo("15");
        assertThat(ledger).containsExactly("DEBIT:30:ADMIN_RESETALL", "CREDIT:5:ADMIN_GIVEALL");
    }

    @Test
    void theGivesAndCreditsOfACurrencyWaitWhileItsResetCannotBeApplied() {
        purse.held = new BigDecimal("40");
        purse.resetAnswers = false;
        queue.add("reset", ItemDeliveryQueue.Kind.RESET, "10");
        queue.add("give", ItemDeliveryQueue.Kind.GIVE, "5");

        deliver.onJoin(ALEX);

        assertThat(purse.held).isEqualByComparingTo("40");
        assertThat(ledger).isEmpty();
        assertThat(queue.rows.values())
                .allSatisfy(row -> assertThat(row.claimant).isNull());
        assertThat(deliver.heldCurrencies(ALEX)).containsExactly("gold");
        assertThat(creditsPaid).isEmpty();

        purse.resetAnswers = true;
        deliver.toOnline(List.of(ALEX));

        assertThat(purse.held).isEqualByComparingTo("15");
        assertThat(deliver.heldCurrencies(ALEX)).isEmpty();
        // The credits that waited for the reset are paid at once, not on the next join.
        assertThat(creditsPaid).containsExactly(ALEX);
    }

    @Test
    void aResetThatKeepsFailingIsReportedOnceAsAnError() {
        purse.resetAnswers = false;
        queue.add("reset", ItemDeliveryQueue.Kind.RESET, "10");

        deliver.onJoin(ALEX);
        deliver.onJoin(ALEX);
        deliver.toOnline(List.of(ALEX));

        assertThat(log.errors())
                .filteredOn(line -> line.contains("swept_items_reset_stuck"))
                .hasSize(1);
    }

    @Test
    void eachHandOverRaisesTheWalletEventOfWhatMoved() {
        purse.held = new BigDecimal("40");
        queue.add("reset", ItemDeliveryQueue.Kind.RESET, "10");
        queue.add("give", ItemDeliveryQueue.Kind.GIVE, "5");

        deliver.onJoin(ALEX);

        assertThat(events.published()).hasSize(2);
        assertThat(events.published().get(0)).isInstanceOfSatisfying(WalletDebited.class, debit -> {
            assertThat(debit.owner()).isEqualTo(ALEX);
            assertThat(debit.amount().amount()).isEqualByComparingTo("30");
            assertThat(debit.resulting().amount()).isEqualByComparingTo("10");
            assertThat(debit.counted()).isFalse();
        });
        assertThat(events.published().get(1)).isInstanceOfSatisfying(WalletCredited.class, credit -> {
            assertThat(credit.amount().amount()).isEqualByComparingTo("5");
            assertThat(credit.resulting().amount()).isEqualByComparingTo("15");
        });
    }

    @Test
    void aHandOverWhoseFinishFailedIsStoredLaterAndNeverHandedOverAgain() {
        queue.add("d1", ItemDeliveryQueue.Kind.GIVE, "25");
        queue.finishFailures = 2;
        // The claim reads as stale to the queue, the way it would once the stale window passed without a refresh.
        queue.claimsLookStale = true;

        deliver.onJoin(ALEX);
        deliver.onJoin(ALEX);

        assertThat(purse.held).isEqualByComparingTo("25");
        assertThat(queue.rows).containsKey("d1");

        timers.runAll();

        assertThat(queue.rows).isEmpty();
        assertThat(queue.touched).isPositive();
        deliver.onJoin(ALEX);
        assertThat(purse.held).isEqualByComparingTo("25");
        assertThat(ledger).containsExactly("CREDIT:25:ADMIN_GIVEALL");
    }

    @Test
    void aGiveToThePlayersOnlineKeepsWhatDoesNotFitForTheNextJoin() {
        purse.room = new BigDecimal("15");

        DeliverSweptItems.OnlineGive given =
                deliver.giveNow(List.of(ALEX), Money.of(GOLD, new BigDecimal("25")), EconomyReason.ADMIN_GIVEALL);

        assertThat(given).isEqualTo(new DeliverSweptItems.OnlineGive(1, 1));
        assertThat(purse.held).isEqualByComparingTo("15");
        assertThat(queue.rows.values()).singleElement().satisfies(row -> {
            assertThat(row.delivery.amount()).isEqualByComparingTo("10");
            assertThat(row.heldBack).isTrue();
        });
    }

    @Test
    void aCurrencyNoLongerDeliveredThisWayKeepsItsDeliveriesWaiting() {
        deferred = false;
        queue.add("d1", ItemDeliveryQueue.Kind.GIVE, "25");

        deliver.onJoin(ALEX);

        assertThat(purse.held).isEqualByComparingTo("0");
        assertThat(queue.rows).containsKey("d1");
    }

    @Test
    void aDeliveryAnotherServerClaimedIsNotHandedOverHere() {
        queue.add("d1", ItemDeliveryQueue.Kind.GIVE, "25");
        queue.claimedElsewhere.add("d1");

        deliver.onJoin(ALEX);

        assertThat(purse.held).isEqualByComparingTo("0");
        assertThat(ledger).isEmpty();
    }

    @Test
    void aPlayerWhoLeftBeforeTheHandOverKeepsTheDelivery() {
        queue.add("d1", ItemDeliveryQueue.Kind.GIVE, "25");
        purse.reachable = false;

        deliver.onJoin(ALEX);

        assertThat(queue.rows.values())
                .singleElement()
                .satisfies(row -> assertThat(row.claimant).isNull());
        assertThat(ledger).isEmpty();
    }

    /** The queue in memory, keeping each row's claim the way the table does. */
    private static final class Queue implements ItemDeliveryQueue {

        final Map<String, Row> rows = new LinkedHashMap<>();
        final List<String> claimedElsewhere = new ArrayList<>();
        int finishFailures;
        boolean claimsLookStale;
        int touched;

        void add(String id, Kind kind, String amount) {
            EconomyReason reason = kind == Kind.RESET ? EconomyReason.ADMIN_RESETALL : EconomyReason.ADMIN_GIVEALL;
            rows.put(id, new Row(new ItemDelivery(id, ALEX.uuid(), "gold", kind, new BigDecimal(amount), reason)));
        }

        @Override
        public List<ItemDelivery> claimable(UUID owner, boolean withHeldBack) {
            return rows.values().stream()
                    .filter(row -> open(row) && (withHeldBack || !row.heldBack))
                    .map(row -> row.delivery)
                    .toList();
        }

        @Override
        public List<UUID> waiting(List<UUID> owners) {
            return rows.values().stream()
                    .filter(row -> !row.heldBack && owners.contains(row.delivery.owner()))
                    .map(row -> row.delivery.owner())
                    .distinct()
                    .toList();
        }

        @Override
        public Set<String> resetsWaiting(UUID owner) {
            return rows.values().stream()
                    .filter(row -> row.delivery.kind() == Kind.RESET
                            && row.delivery.owner().equals(owner))
                    .map(row -> row.delivery.currency())
                    .collect(Collectors.toSet());
        }

        @Override
        public void queueGive(
                String sweepId, List<UUID> owners, String currency, BigDecimal amount, EconomyReason reason) {
            for (UUID owner : owners) {
                String id = sweepId + ":" + owner;
                rows.put(id, new Row(new ItemDelivery(id, owner, currency, Kind.GIVE, amount, reason)));
            }
        }

        @Override
        public Optional<ItemDelivery> claim(String id, String claimant) {
            Row row = rows.get(id);
            if (row == null || !open(row) || claimedElsewhere.contains(id)) {
                return Optional.empty();
            }
            row.claimant = claimant;
            return Optional.of(row.delivery);
        }

        @Override
        public boolean touch(String id, String claimant) {
            Row row = rows.get(id);
            if (row == null || !claimant.equals(row.claimant)) {
                return false;
            }
            touched++;
            return true;
        }

        @Override
        public boolean finish(String id, String claimant, BigDecimal rest) {
            if (finishFailures > 0) {
                finishFailures--;
                throw new IllegalStateException("database unreachable");
            }
            Row row = rows.get(id);
            if (row == null || !claimant.equals(row.claimant)) {
                return false;
            }
            if (rest.signum() == 0) {
                rows.remove(id);
                return true;
            }
            ItemDelivery was = row.delivery;
            Row kept = new Row(new ItemDelivery(id, was.owner(), was.currency(), Kind.GIVE, rest, was.reason()));
            kept.heldBack = true;
            rows.put(id, kept);
            return true;
        }

        @Override
        public void release(String id, String claimant) {
            Row row = rows.get(id);
            if (row != null && claimant.equals(row.claimant)) {
                row.claimant = null;
            }
        }

        private boolean open(Row row) {
            return row.claimant == null || claimsLookStale;
        }

        static final class Row {
            final ItemDelivery delivery;

            @Nullable String claimant;

            boolean heldBack;

            Row(ItemDelivery delivery) {
                this.delivery = delivery;
            }
        }
    }

    /** One player's held gold, with only so much room left in the inventory. */
    private static final class Purse implements ItemPurse {

        BigDecimal held = BigDecimal.ZERO;
        BigDecimal room = new BigDecimal("1000");
        boolean reachable = true;
        boolean resetAnswers = true;

        @Override
        public Optional<Change> give(PlayerRef owner, Money amount) {
            if (!reachable) {
                return Optional.empty();
            }
            BigDecimal before = held;
            BigDecimal fit = amount.amount().min(room);
            held = held.add(fit);
            room = room.subtract(fit);
            return Optional.of(new Change(before, held));
        }

        @Override
        public Optional<Change> setTo(PlayerRef owner, Money target) {
            if (!reachable || !resetAnswers) {
                return Optional.empty();
            }
            BigDecimal before = held;
            BigDecimal diff = target.amount().subtract(held);
            if (diff.signum() < 0) {
                held = target.amount();
                room = room.add(diff.negate());
            } else {
                BigDecimal fit = diff.min(room);
                held = held.add(fit);
                room = room.subtract(fit);
            }
            return Optional.of(new Change(before, held));
        }
    }

    private record Online(Map<UUID, Boolean> online) implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return online.getOrDefault(uuid, false);
        }
    }

    /** The ledger rows written, as {@code TYPE:amount:REASON}. */
    private record Ledger(List<String> rows) implements TransactionHistory {

        @Override
        public List<HistoryRecord> queryTransactions(UUID playerUuid, int limit, int offset) {
            return List.of();
        }

        @Override
        public List<HistoryRecord> queryGlobalTransactions(int limit, int offset) {
            return List.of();
        }

        @Override
        public void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at) {}

        @Override
        public void recordCredit(String ownerId, Money amount, EconomyReason reason, long at) {
            rows.add("CREDIT:" + amount.amount().stripTrailingZeros().toPlainString() + ":" + reason);
        }

        @Override
        public void recordDebit(String ownerId, Money amount, EconomyReason reason, long at) {
            rows.add("DEBIT:" + amount.amount().stripTrailingZeros().toPlainString() + ":" + reason);
        }

        @Override
        public List<HistoryRecord> queryBankTransactions(String bankId, int limit, int offset) {
            return List.of();
        }

        @Override
        public void flush() {}
    }
}
