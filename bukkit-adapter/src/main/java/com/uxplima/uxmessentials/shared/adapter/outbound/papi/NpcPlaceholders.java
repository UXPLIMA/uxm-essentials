package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.OptionalInt;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code npc_*} placeholders: how many NPCs stand on the server, how many
 * of them one player owns, and the quota that player creates against. Wired during npc wiring; with the module
 * disabled the seam is absent and every key degrades to the dash.
 */
public interface NpcPlaceholders {

    /** Every NPC the server holds, however it was created. */
    int total();

    /** How many NPCs {@code who} owns, which is what their quota counts. */
    int owned(PlayerRef who);

    /** {@code who}'s NPC quota, or empty when they may create any number. */
    OptionalInt limit(PlayerRef who);
}
