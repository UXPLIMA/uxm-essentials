package com.uxplima.uxmessentials.vaults.application;

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

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemReceiver;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemReturns;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.junit.jupiter.api.Test;

/**
 * Returned vault items reach the owner exactly once: into their inventory as far as it has room, and the rest, or all
 * of it while they are away, waits in storage for their next join. Nothing is dropped.
 */
class ReturnVaultItemsTest {

    /** Three items, one per byte. */
    private static final VaultContents THREE = VaultContents.of(new byte[] {1, 2, 3});

    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final MemoryReturns store = new MemoryReturns();
    private final Pocket pocket = new Pocket();
    private final List<String> lines = new ArrayList<>();
    private boolean running = true;

    private ReturnVaultItems returns() {
        VaultNotifier notifier = new VaultNotifier(
                (viewer, key, placeholders) -> {
                    lines.add(key.key() + " " + placeholders.get("count"));
                    return key.key();
                },
                (viewer, text) -> {});
        return new ReturnVaultItems(scheduler, store, pocket, notifier, new QuietLog(), () -> running);
    }

    @Test
    void anOnlineOwnerGetsTheItemsInTheirInventory() {
        pocket.online(ALEX, 10);

        returns().handBack(ALEX, THREE);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(store.count(ALEX)).isZero();
        assertThat(lines).containsExactly("vaults.deleted-returned 3");
    }

    @Test
    void whatDoesNotFitWaitsForTheNextJoinInsteadOfBeingDropped() {
        pocket.online(ALEX, 1);

        returns().handBack(ALEX, THREE);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1);
        assertThat(store.items(ALEX)).containsExactly(List.of((byte) 2, (byte) 3));
        assertThat(lines).containsExactly("vaults.deleted-returned 1", "vaults.returns-kept 2");

        pocket.online(ALEX, 10);
        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(store.count(ALEX)).isZero();
    }

    @Test
    void anOwnerWhoIsAwayFindsTheItemsOnTheirNextJoin() {
        returns().handBack(ALEX, THREE);
        scheduler.runAll();
        assertThat(store.count(ALEX)).isEqualTo(1);

        pocket.online(ALEX, 10);
        returns().deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(store.count(ALEX)).isZero();
    }

    @Test
    void anOwnerWhoLeavesBeforeTheItemsArriveKeepsThemForLater() {
        pocket.online(ALEX, 10);
        store.keep(ALEX.uuid(), THREE);

        returns().deliverKept(ALEX);
        scheduler.leave(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEmpty();
        assertThat(store.count(ALEX)).isEqualTo(1);
    }

    @Test
    void twoServersDeliveringAtOnceHandEachBatchOverOnce() {
        pocket.online(ALEX, 10);
        store.keep(ALEX.uuid(), THREE);
        ReturnVaultItems lobby = returns();
        ReturnVaultItems survival = returns();
        store.pauseReads();

        lobby.deliverKept(ALEX);
        survival.deliverKept(ALEX);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(store.count(ALEX)).isZero();
    }

    @Test
    void aGiveThatFailsKeepsTheItems() {
        pocket.online(ALEX, 10);
        pocket.breakNextGive();

        returns().handBack(ALEX, THREE);
        scheduler.runAll();

        assertThat(pocket.held(ALEX)).isEmpty();
        assertThat(store.items(ALEX)).containsExactly(List.of((byte) 1, (byte) 2, (byte) 3));
    }

    @Test
    void onceThePluginHasStoppedTheItemsAreKeptOnTheCallingThread() {
        running = false;
        pocket.online(ALEX, 10);

        returns().handBack(ALEX, THREE);

        assertThat(store.count(ALEX)).isEqualTo(1);
        assertThat(pocket.held(ALEX)).isEmpty();
    }

    /** Kept batches by key, claimed by removing them. */
    private static final class MemoryReturns implements VaultItemReturns {

        private final Map<String, Map.Entry<UUID, VaultContents>> rows = new LinkedHashMap<>();
        private int ids;
        private boolean pausedReads;
        private List<Kept> frozen = List.of();

        /** Answer every read with the rows as they are now, as two servers reading at the same moment would. */
        void pauseReads() {
            pausedReads = true;
            frozen = read(ALEX.uuid());
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
    private static final class Pocket implements VaultItemReceiver {

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
