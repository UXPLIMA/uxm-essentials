package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.ContainerLayout;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.OfflineSnapshot;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveOutcome;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Outbound port for an <em>offline</em> player's stored inventory and ender chest, the data {@code /invsee} and
 * {@code /endersee} reach for when the target is not online here and so has no live inventory.
 *
 * <p>Both methods touch the disk and run on the async lane. Neither throws: a failure is logged and comes back as an
 * outcome the caller reports to the staff member.
 */
@NullMarked
public interface OfflinePlayerStorage {

    /** Read {@code player}'s container as a window would show it. */
    OfflineSnapshot read(UUID player, ContainerLayout layout);

    /**
     * Save a window that was opened on {@code base} and now holds {@code contents}: the slots that differ from
     * {@code base} are written, provided each still holds what {@code base} saw. Everything else is left as it is.
     */
    SaveOutcome save(UUID player, ContainerLayout layout, OfflineSnapshot base, @Nullable ItemStack[] contents);

    /** The storage for a server whose internals could not be reached: nothing can be read or saved. */
    static OfflinePlayerStorage unavailable() {
        return new OfflinePlayerStorage() {
            @Override
            public OfflineSnapshot read(UUID player, ContainerLayout layout) {
                return OfflineSnapshot.unreadable(SaveOutcome.UNAVAILABLE, layout);
            }

            @Override
            public SaveOutcome save(
                    UUID player, ContainerLayout layout, OfflineSnapshot base, @Nullable ItemStack[] contents) {
                return SaveOutcome.UNAVAILABLE;
            }
        };
    }
}
