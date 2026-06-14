package com.uxplima.uxmessentials.staff.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;

/**
 * Outbound port wrapping the Bukkit inventory operations staff mode performs on a live player: snapshotting
 * the current loadout, restoring a stored one, and laying out the gadget hotbar. The application never
 * touches a {@code Player} or an {@code ItemStack}; it asks this port, whose adapter reads and writes the
 * live entity (encoding/decoding the item blobs through the same {@code VaultItemCodec} the vaults context
 * uses).
 *
 * <p>Ordering is load-bearing and owned by the {@code EnterStaffMode} use case: {@link #capture} runs first,
 * the result is committed to {@link StaffLoadoutRepository}, and only then does {@link #applyGadgetHotbar}
 * overwrite the live inventory — so the real loadout is durable before it is swapped away.
 */
public interface StaffLoadoutCapture {

    /** Snapshot {@code who}'s current loadout into a pure {@link SavedLoadout}. */
    SavedLoadout capture(PlayerRef who);

    /** Restore {@code loadout} onto {@code who}, replacing whatever they are currently holding. */
    void restore(PlayerRef who, SavedLoadout loadout);

    /** Clear {@code who}'s inventory and lay out the gadget hotbar for the {@code modeName} profile. */
    void applyGadgetHotbar(PlayerRef who, String modeName);
}
