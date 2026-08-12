package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code villagers_*} placeholders: how many villagers are walking after
 * this player right now. It is the module's one piece of per-player runtime state, and the one thing worth putting
 * on a HUD, because a follow that quietly ended (the villager died, the owner walked out of range) is invisible
 * otherwise.
 *
 * <p>Wired during villagers wiring, and only when the follow sub-feature is on: with the module or the feature off
 * the seam is absent and the keys degrade to the dash.
 */
public interface VillagersPlaceholders {

    /** How many villagers are currently following {@code who}. */
    int following(PlayerRef who);
}
