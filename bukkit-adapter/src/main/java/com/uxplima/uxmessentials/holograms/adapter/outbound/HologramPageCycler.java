package com.uxplima.uxmessentials.holograms.adapter.outbound;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.holograms.domain.HologramName;
import org.jspecify.annotations.NullMarked;

/**
 * Advances one viewer to the next page of a multi-page hologram and re-sends them their page text. Implemented
 * by {@link HologramRenderer} (it owns the live entity and the per-viewer override path) and called by the
 * click listener when a viewer right-clicks a paged hologram that carries no click command, so paging stays a
 * pure rendering concern with no domain change or save.
 */
@NullMarked
public interface HologramPageCycler {

    /** Advance {@code viewer} to the next page of the multi-page hologram {@code name} and re-send their text. */
    void cyclePage(Player viewer, HologramName name);
}
