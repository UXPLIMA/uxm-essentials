package com.uxplima.uxmessentials.trade.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.trade.application.port.TradeItemReceiver;
import com.uxplima.uxmessentials.trade.application.port.TradeItemReturns;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Items a trade could not hand to a player who was gone reach that player exactly once: kept in storage, handed over
 * on their next join on whichever server they join, and left in storage whenever a hand-over does not land. Each byte
 * of a payload stands for one item, so the bytes across the inventory and the store prove nothing was duplicated or
 * lost.
 */
class ReturnTradeItemsTest {

    /** A sword, a named stack and a shulker: three items, one per byte. */
    private static final VaultContents STAKE = VaultContents.of(new byte[] {1, 2, 3});

    private static final List<Byte> ALL = List.of((byte) 1, (byte) 2, (byte) 3);

    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final MemoryReturns store = new MemoryReturns();
    private final Pocket pocket = new Pocket();
    private final List<String> lines = new ArrayList<>();
    private boolean running = true;

    private ReturnTradeItems returns() {
        return returnsOn(scheduler);
    }

    private ReturnTradeItems returnsOn(QueuedScheduler lane) {
        Notifier notifier = new Notifier(
                (viewer, key, placeholders) -> {
                    lines.add(key.key() + " " + placeholders.get("count"));
                    return key.key();
                },
                (viewer, text) -> {});
        return new ReturnTradeItems(lane, store, pocket, notifier, new QuietLog(), () -> running);
    }

    @Test
    void itemsKeptForAPlayerWhoLeftReachThemOnceOnTheirNextJoin() {
        returns().keep(ALEX, STAKE);
        scheduler.runAll();
        assertThat(store.items(ALEX)).containsExactly(ALL);

        pocket.online(ALEX, 10);
        returns().deliverKept(ALEX);
        scheduler.runAll();
        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEqualTo(ALL);
        assertThat(store.count(ALEX)).isZero();
        assertThat(lines).containsExactly("trade.returns-delivered 3");
    }

    @Test
    void joiningTwoServersInQuickSuccessionHandsTheItemsOverOnce() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 10);
        ReturnTradeItems lobby = returns();
        ReturnTradeItems survival = returns();

        lobby.deliverKept(ALEX);
        survival.deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEqualTo(ALL);
        assertThat(store.count(ALEX)).isZero();
    }

    @Test
    void aGiveThatFailsReleasesTheBatchForTheNextJoin() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 10);
        pocket.breakNextGive();

        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEmpty();
        assertThat(store.items(ALEX)).containsExactly(ALL);
        assertThat(store.claimedCount()).isZero();

        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEqualTo(ALL);
        assertThat(store.count(ALEX)).isZero();
    }

    @Test
    void aPlayerWhoLeavesBeforeTheHandOverFindsTheBatchOnTheirNextJoin() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 10);

        returns().deliverKept(ALEX);
        scheduler.leave(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEmpty();
        assertThat(store.items(ALEX)).containsExactly(ALL);
        assertThat(store.claimedCount()).isZero();
    }

    @Test
    void aServerThatCrashedAfterItsClaimLeavesTheBatchForALaterJoinOnceTheClaimIsStale() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 10);
        // The claim is taken, then the server dies before its hop to the player's thread runs.
        returnsOn(new QueuedScheduler()).deliverKept(ALEX);

        returns().deliverKept(ALEX);
        scheduler.runAll();
        assertThat(pocket.held(ALEX)).isEmpty();

        store.expireClaims();
        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEqualTo(ALL);
        assertThat(store.count(ALEX)).isZero();
    }

    @Test
    void whatDoesNotFitStaysKeptInPlaceOfTheBatchRatherThanDropped() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 1);

        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1);
        assertThat(store.items(ALEX)).containsExactly(List.of((byte) 2, (byte) 3));
        assertThat(store.claimedCount()).isZero();
        assertThat(lines).containsExactly("trade.returns-delivered 1", "trade.returns-kept 2");
    }

    @Test
    void aBatchKeptAfterTheJoinReadWaitsForTheFollowingJoin() {
        pocket.online(ALEX, 10);
        returns().deliverKept(ALEX);
        returns().keep(ALEX, STAKE);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEmpty();
        assertThat(store.count(ALEX)).isEqualTo(1);

        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEqualTo(ALL);
        assertThat(store.count(ALEX)).isZero();
    }

    @Test
    void aStoreThatCannotBeReadLeavesTheRowsForTheNextJoin() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 10);
        store.failReads = true;

        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEmpty();
        store.failReads = false;
        assertThat(store.count(ALEX)).isEqualTo(1);
    }

    @Test
    void onceThePluginHasStoppedTheItemsAreKeptOnTheCallingThread() {
        running = false;

        returns().keep(ALEX, STAKE);

        assertThat(store.count(ALEX)).isEqualTo(1);
    }

    /** Kept batches by key, each with the token of the delivery that claimed it, if any. */
    private static final class MemoryReturns implements TradeItemReturns {

        private final Map<String, Row> rows = new LinkedHashMap<>();
        private int ids;
        boolean failReads;

        private static final class Row {
            final UUID owner;
            VaultContents items;

            @Nullable String claimedBy;

            boolean stale;

            Row(UUID owner, VaultContents items) {
                this.owner = owner;
                this.items = items;
            }

            boolean open() {
                return claimedBy == null || stale;
            }
        }

        /** Every claim taken so far is now older than the stale window. */
        void expireClaims() {
            rows.values().forEach(row -> row.stale = row.claimedBy != null);
        }

        int count(PlayerRef owner) {
            return items(owner).size();
        }

        long claimedCount() {
            return rows.values().stream().filter(row -> row.claimedBy != null).count();
        }

        List<List<Byte>> items(PlayerRef owner) {
            return rows.values().stream()
                    .filter(row -> row.owner.equals(owner.uuid()))
                    .map(row -> asList(row.items))
                    .toList();
        }

        @Override
        public void keep(UUID owner, VaultContents items) {
            rows.put("r" + ids++, new Row(owner, items));
        }

        @Override
        public List<Kept> claimable(UUID owner) {
            if (failReads) {
                throw new IllegalStateException("database unavailable");
            }
            List<Kept> kept = new ArrayList<>();
            rows.forEach((id, row) -> {
                if (row.owner.equals(owner) && row.open()) {
                    kept.add(new Kept(id, row.items));
                }
            });
            return kept;
        }

        @Override
        public boolean claim(String id, String claimant) {
            Row row = rows.get(id);
            if (row == null || !row.open()) {
                return false;
            }
            row.claimedBy = claimant;
            row.stale = false;
            return true;
        }

        @Override
        public boolean finish(String id, String claimant, VaultContents rest) {
            Row row = rows.get(id);
            if (row == null || !claimant.equals(row.claimedBy)) {
                return false;
            }
            if (rest.isEmpty()) {
                rows.remove(id);
            } else {
                row.items = rest;
                row.claimedBy = null;
            }
            return true;
        }

        @Override
        public void release(String id, String claimant) {
            Row row = rows.get(id);
            if (row != null && claimant.equals(row.claimedBy)) {
                row.claimedBy = null;
            }
        }
    }

    /** Inventories with room for a number of items; each byte of a payload is one item. */
    private static final class Pocket implements TradeItemReceiver {

        private final Map<UUID, Integer> room = new LinkedHashMap<>();
        private final Map<UUID, List<Byte>> held = new LinkedHashMap<>();
        private final Set<UUID> breaks = new HashSet<>();

        void online(PlayerRef player, int capacity) {
            room.put(player.uuid(), capacity);
        }

        void breakNextGive() {
            breaks.add(ALEX.uuid());
        }

        List<Byte> held(PlayerRef player) {
            return held.getOrDefault(player.uuid(), List.of());
        }

        @Override
        public Optional<Receipt> give(PlayerRef owner, VaultContents items) {
            if (breaks.remove(owner.uuid())) {
                throw new IllegalStateException("the inventory refused the items");
            }
            Integer capacity = room.get(owner.uuid());
            if (capacity == null) {
                return Optional.empty();
            }
            List<Byte> mine = held.computeIfAbsent(owner.uuid(), id -> new ArrayList<>());
            List<Byte> incoming = asList(items);
            int fits = Math.min(capacity - mine.size(), incoming.size());
            mine.addAll(incoming.subList(0, fits));
            List<Byte> rest = incoming.subList(fits, incoming.size());
            byte[] left = new byte[rest.size()];
            for (int i = 0; i < left.length; i++) {
                left[i] = rest.get(i);
            }
            VaultContents leftover = left.length == 0 ? VaultContents.empty() : VaultContents.of(left);
            return Optional.of(new Receipt(fits, leftover, left.length));
        }
    }

    private static List<Byte> asList(VaultContents items) {
        byte[] bytes = items.payload().orElse(new byte[0]);
        Byte[] boxed = new Byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            boxed[i] = bytes[i];
        }
        return Arrays.asList(boxed);
    }
}
