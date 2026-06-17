package com.uxplima.uxmessentials.npc.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound port the npc context drives to send the operator to an NPC's location — the inverse of {@code /npc
 * movehere}. The use case resolves which NPC was asked for and hands its {@link Position} here; the adapter behind
 * this port performs the region-aware async hop ({@code teleportAsync} on the right Folia region thread), keeping
 * every Bukkit teleport concern out of the application layer.
 *
 * <p>Fire-and-forget — the method returns {@code void}; the adapter is responsible for landing the player.
 */
public interface NpcTeleporter {

    /** Send {@code who} to {@code destination}, hopping to the destination's region thread first (Folia). */
    void teleport(PlayerRef who, Position destination);
}
