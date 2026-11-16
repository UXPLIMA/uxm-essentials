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
import org.junit.jupiter.api.Test;

/**
 * Items a trade could not hand to a player who was gone reach that player exactly once: kept in storage, handed over
 * on their next join on whichever server they join, and kept again whenever a hand-over does not land. Each byte of a
 * payload stands for one item, so the count of every byte across the inventory and the store proves nothing was
 * duplicated or lost.
 */
class ReturnTradeItemsTest {

    /** A sword, a named stack and a shulker: three items, one per byte. */
    private static final VaultContents STAKE = VaultContents.of(new byte[] {1, 2, 3});

    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final MemoryReturns store = new MemoryReturns();
    private final Pocket pocket = new Pocket();
    private final List<String> lines = new ArrayList<>();
    private boolean running = true;

    private ReturnTradeItems returns() {
        Notifier notifier = new Notifier(
                (viewer, key, placeholders) -> {
                    lines.add(key.key() + " " + placeholders.get("count"));
                    return key.key();
                },
                (viewer, text) -> {});
        return new ReturnTradeItems(scheduler, store, pocket, notifier, new QuietLog(), () -> running);
    }

    @Test
    void itemsKeptForAPlayerWhoLeftReachThemOnceOnTheirNextJoin() {
        returns().keep(ALEX, STAKE);
        scheduler.runAll();
        assertThat(store.items(ALEX)).containsExactly(List.of((byte) 1, (byte) 2, (byte) 3));

        pocket.online(ALEX, 10);
        returns().deliverKept(ALEX);
        scheduler.runAll();
        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(store.count(ALEX)).isZero();
        assertThat(lines).containsExactly("trade.returns-delivered 3");
    }

    @Test
    void joiningTwoServersInQuickSuccessionHandsTheItemsOverOnce() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 10);
        ReturnTradeItems lobby = returns();
        ReturnTradeItems survival = returns();
        store.pauseReads();

        lobby.deliverKept(ALEX);
        survival.deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(store.count(ALEX)).isZero();
    }

    @Test
    void aGiveThatFailsKeepsTheWholeBatchForTheNextJoin() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 10);
        pocket.breakNextGive();

        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEmpty();
        assertThat(store.items(ALEX)).containsExactly(List.of((byte) 1, (byte) 2, (byte) 3));

        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(store.count(ALEX)).isZero();
    }

    @Test
    void aPlayerWhoLeavesBeforeTheHandOverKeepsTheItemsForLater() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 10);

        returns().deliverKept(ALEX);
        scheduler.leave(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEmpty();
        assertThat(store.items(ALEX)).containsExactly(List.of((byte) 1, (byte) 2, (byte) 3));
    }

    @Test
    void whatDoesNotFitIsKeptRatherThanDropped() {
        store.keep(ALEX.uuid(), STAKE);
        pocket.online(ALEX, 1);

        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1);
        assertThat(store.items(ALEX)).containsExactly(List.of((byte) 2, (byte) 3));
        assertThat(lines).containsExactly("trade.returns-delivered 1", "trade.returns-kept 2");
    }

    @Test
    void aBatchKeptAfterTheJoinReadWaitsForTheFollowingJoin() {
        pocket.online(ALEX, 10);
        store.pauseReads();
        returns().deliverKept(ALEX);
        returns().keep(ALEX, STAKE);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEmpty();
        assertThat(store.count(ALEX)).isEqualTo(1);

        store.resumeReads();
        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1, (byte) 2, (byte) 3);
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

    /** Kept batches by key, claimed by removing them. */
    private static final class MemoryReturns implements TradeItemReturns {

        private final Map<String, Map.Entry<UUID, VaultContents>> rows = new LinkedHashMap<>();
        private int ids;
        private boolean pausedReads;
        private List<Kept> frozen = List.of();
        boolean failReads;

        /** Answer every read with the rows as they are now, as two servers reading at the same moment would. */
        void pauseReads() {
            pausedReads = true;
            frozen = read(ALEX.uuid());
        }

        void resumeReads() {
            pausedReads = false;
        }

        int count(PlayerRef owner) {
            return read(owner.uuid()).size();
        }

        List<List<Byte>> items(PlayerRef owner) {
            return read(owner.uuid()).stream().map(kept -> asList(kept.items())).toList();
        }

        @Override
        public void keep(UUID owner, VaultContents items) {
            rows.put("r" + ids++, Map.entry(owner, items));
        }

        @Override
        public List<Kept> kept(UUID owner) {
            if (failReads) {
                throw new IllegalStateException("database unavailable");
            }
            return pausedReads ? frozen : read(owner);
        }

        @Override
        public boolean claim(String id) {
            return rows.remove(id) != null;
        }

        private List<Kept> read(UUID owner) {
            List<Kept> kept = new ArrayList<>();
            rows.forEach((id, row) -> {
                if (row.getKey().equals(owner)) {
                    kept.add(new Kept(id, row.getValue()));
                }
            });
            return kept;
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
