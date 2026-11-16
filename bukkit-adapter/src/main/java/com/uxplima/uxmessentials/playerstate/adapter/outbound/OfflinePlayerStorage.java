package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.ContainerLayout;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.OfflineSnapshot;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveOutcome;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Outbound port for an <em>offline</em> player's stored inventory and ender chest, the data {@code /invsee} and
 * {@code /endersee} reach for when the target is not online here and so has no live inventory.
 *
 * <p>Both methods touch the disk. Neither throws: a failure is logged and comes back as an outcome the caller reports
 * to the staff member.
 */
@NullMarked
public interface OfflinePlayerStorage {

    /** Read {@code player}'s container as a window would show it. */
    OfflineSnapshot<ItemStack> read(UUID player, ContainerLayout layout);

    /**
     * Write {@code changes} (slot to new item, {@code null} to empty it) to a container that was read as
     * {@code base}. A slot that no longer holds what {@code base} saw is a conflict and is not written; with
     * conflicts, the other slots are written only if {@code partly} accepts the conflicting ones.
     */
    SaveResult save(
            UUID player,
            ContainerLayout layout,
            OfflineSnapshot<ItemStack> base,
            Map<Integer, @Nullable ItemStack> changes,
            Predicate<Set<Integer>> partly);

    /** The storage for a server whose internals could not be reached: nothing can be read or saved. */
    static OfflinePlayerStorage unavailable() {
        return new OfflinePlayerStorage() {
            @Override
            public OfflineSnapshot<ItemStack> read(UUID player, ContainerLayout layout) {
                return OfflineSnapshot.unreadable(SaveOutcome.UNAVAILABLE, new ItemStack[0]);
            }

            @Override
            public SaveResult save(
                    UUID player,
                    ContainerLayout layout,
                    OfflineSnapshot<ItemStack> base,
                    Map<Integer, @Nullable ItemStack> changes,
                    Predicate<Set<Integer>> partly) {
                return SaveResult.of(SaveOutcome.UNAVAILABLE);
            }
        };
    }
}
