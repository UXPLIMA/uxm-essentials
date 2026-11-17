package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.itemworld.domain.ItemworldMessageKey;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.ContainerLayout;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.OfflineSnapshot;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveOutcome;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveResult;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.StoredSlots;
import com.uxplima.uxmessentials.playerstate.domain.PlayerstateMessageKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The ender chest and inventory a player left on this server can be copied and cleared while they are away, and
 * nothing is written over an item the server cannot read or over a slot that changed in the meantime.
 */
class OfflineContainersTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    private Stored stored;
    private OfflineContainers containers;

    @BeforeEach
    void start() {
        MockBukkit.mock();
        stored = new Stored();
        containers = new OfflineContainers(() -> stored);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void aClearEmptiesEverySlotOfTheStoredContainer() {
        stored.items[0] = new ItemStack(Material.DIAMOND, 3);
        stored.items[26] = new ItemStack(Material.EMERALD);

        assertThat(containers.clear(PLAYER, ContainerLayout.ENDER)).isEqualTo(OfflineContainers.Outcome.DONE);

        assertThat(stored.items).containsOnlyNulls();
    }

    @Test
    void aCopyReadsEverySlotOfTheStoredContainer() {
        stored.items[4] = new ItemStack(Material.DIAMOND, 3);

        OfflineContainers.Read read = containers.read(PLAYER, ContainerLayout.ENDER);

        assertThat(read.outcome()).isEqualTo(OfflineContainers.Outcome.DONE);
        assertThat(read.items()).hasSize(27);
        assertThat(read.items()[4]).isEqualTo(new ItemStack(Material.DIAMOND, 3));
    }

    @Test
    void aContainerHoldingAnItemTheServerCannotReadIsLeftAsItIs() {
        stored.items[0] = new ItemStack(Material.DIAMOND);
        stored.editable = false;

        OfflineContainers.Outcome cleared = containers.clear(PLAYER, ContainerLayout.ENDER);

        assertThat(cleared).isEqualTo(OfflineContainers.Outcome.NOT_UNDERSTOOD);
        assertThat(cleared.problem()).contains(ItemworldMessageKey.OFFLINE_NOT_UNDERSTOOD);
        assertThat(stored.items[0]).isEqualTo(new ItemStack(Material.DIAMOND));
        assertThat(containers.read(PLAYER, ContainerLayout.ENDER).outcome())
                .isEqualTo(OfflineContainers.Outcome.NOT_UNDERSTOOD);
    }

    @Test
    void aSlotThatChangedSinceTheReadMeansNothingIsCleared() {
        stored.items[0] = new ItemStack(Material.DIAMOND);
        stored.items[1] = new ItemStack(Material.EMERALD);
        stored.conflicts = Set.of(1);

        assertThat(containers.clear(PLAYER, ContainerLayout.ENDER)).isEqualTo(OfflineContainers.Outcome.CHANGED);

        assertThat(stored.items[0]).isEqualTo(new ItemStack(Material.DIAMOND));
    }

    @Test
    void aPlayerWithNoDataHereOrJoiningNowIsToldApart() {
        stored.problem = SaveOutcome.NO_DATA;
        assertThat(containers.clear(PLAYER, ContainerLayout.ENDER).problem())
                .contains(PlayerstateMessageKey.OFFLINE_NO_DATA);

        stored.problem = SaveOutcome.PLAYER_PRESENT;
        assertThat(containers.read(PLAYER, ContainerLayout.INVENTORY).outcome().problem())
                .contains(PlayerstateMessageKey.OFFLINE_JOINING);
    }

    @Test
    void aFailedSaveSaysSo() {
        stored.items[0] = new ItemStack(Material.DIAMOND);
        stored.failSave = true;

        assertThat(containers.clear(PLAYER, ContainerLayout.ENDER).problem())
                .contains(ItemworldMessageKey.OFFLINE_SAVE_FAILED);
    }

    /** One player's stored ender chest, with the conflict check of the real storage. */
    private static final class Stored implements OfflinePlayerStorage {

        final @Nullable ItemStack[] items = new ItemStack[ContainerLayout.ENDER.size()];
        boolean editable = true;

        @Nullable SaveOutcome problem;

        Set<Integer> conflicts = Set.of();
        boolean failSave;

        @Override
        public OfflineSnapshot<ItemStack> read(UUID player, ContainerLayout layout) {
            if (problem != null) {
                return OfflineSnapshot.unreadable(problem, new ItemStack[0]);
            }
            @Nullable ItemStack[] copy = Arrays.copyOf(items, layout.size());
            return OfflineSnapshot.found(copy, new StoredSlots(1, Map.of()), editable);
        }

        @Override
        public SaveResult save(
                UUID player,
                ContainerLayout layout,
                OfflineSnapshot<ItemStack> base,
                Map<Integer, @Nullable ItemStack> changes,
                Predicate<Set<Integer>> partly) {
            if (failSave) {
                return SaveResult.of(SaveOutcome.FAILED);
            }
            Set<Integer> hit = new java.util.HashSet<>(conflicts);
            hit.retainAll(changes.keySet());
            if (!hit.isEmpty() && !partly.test(hit)) {
                return new SaveResult(SaveOutcome.CHANGED, hit);
            }
            Map<Integer, @Nullable ItemStack> written = new HashMap<>(changes);
            written.keySet().removeAll(hit);
            written.forEach((slot, item) -> items[slot] = item);
            return new SaveResult(SaveOutcome.SAVED, hit);
        }
    }
}
