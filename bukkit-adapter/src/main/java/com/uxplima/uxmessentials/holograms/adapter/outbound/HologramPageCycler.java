package com.uxplima.uxmessentials.holograms.adapter.outbound;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.holograms.domain.HologramName;
import org.jspecify.annotations.NullMarked;

/**
 * Advances one viewer to the next page of a multi-page hologram and draws them their page. Implemented by
 * {@link PacketHologramView} and called by the click handler when a viewer clicks a paged hologram, so paging stays
 * a drawing concern with no domain change or save.
 */
@NullMarked
public interface HologramPageCycler {

    /** Advance {@code viewer} to the next page of the multi-page hologram {@code name} and draw it for them. */
    void cyclePage(Player viewer, HologramName name);
}
