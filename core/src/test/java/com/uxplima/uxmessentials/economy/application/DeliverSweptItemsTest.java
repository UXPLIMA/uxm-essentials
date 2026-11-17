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
import java.util.UUID;

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
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.QuietLog;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The items a sweep queued reach a player's inventory once, what does not fit waits for their next join instead of
 * falling on the ground, and a reset brings the held amount to the starting figure.
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
    private final DeliverSweptItems deliver = new DeliverSweptItems(
            queue,
            purse,
            CurrencyRegistry.of(List.of(GOLD), GOLD.id()),
            new Ledger(ledger),
            new EconomyNotifier(
                    (PlayerRef viewer, MessageKey key, Map<String, String> values) -> {
                        lines.add(key.key());
                        return key.key();
                    },
                    (viewer, text) -> {}),
            new Online(online),
            new QuietLog(),
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
    void aResetThatNeedsMoreThanFitsKeepsTheShortfallForLater() {
        purse.room = new BigDecimal("4");
        queue.add("d1", ItemDeliveryQueue.Kind.RESET, "10");

        deliver.onJoin(ALEX);

        assertThat(purse.held).isEqualByComparingTo("4");
        assertThat(queue.rows.values()).singleElement().satisfies(row -> {
            assertThat(row.delivery.kind()).isEqualTo(ItemDeliveryQueue.Kind.GIVE);
            assertThat(row.delivery.amount()).isEqualByComparingTo("6");
            assertThat(row.heldBack).isTrue();
        });
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

        void add(String id, Kind kind, String amount) {
            rows.put(id, new Row(new ItemDelivery(id, ALEX.uuid(), "gold", kind, new BigDecimal(amount))));
        }

        @Override
        public List<ItemDelivery> claimable(UUID owner, boolean withHeldBack) {
            return rows.values().stream()
                    .filter(row -> row.claimant == null && (withHeldBack || !row.heldBack))
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
        public boolean claim(String id, String claimant) {
            Row row = rows.get(id);
            if (row == null || row.claimant != null || claimedElsewhere.contains(id)) {
                return false;
            }
            row.claimant = claimant;
            return true;
        }

        @Override
        public boolean finish(String id, String claimant, BigDecimal rest) {
            Row row = rows.get(id);
            if (row == null || !claimant.equals(row.claimant)) {
                return false;
            }
            if (rest.signum() == 0) {
                rows.remove(id);
                return true;
            }
            Row kept = new Row(new ItemDelivery(id, row.delivery.owner(), row.delivery.currency(), Kind.GIVE, rest));
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

        @Override
        public Optional<BigDecimal> give(PlayerRef owner, Money amount) {
            if (!reachable) {
                return Optional.empty();
            }
            BigDecimal fit = amount.amount().min(room);
            held = held.add(fit);
            room = room.subtract(fit);
            return Optional.of(fit);
        }

        @Override
        public Optional<Change> setTo(PlayerRef owner, Money target) {
            if (!reachable) {
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
