package com.uxplima.uxmessentials.shared.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound port for sending an operator straight to a position: {@code /hologram teleport}, {@code /npc
 * teleport} and the like. The use case resolves what was asked for and hands its {@link Position} here; the
 * adapter behind this port performs the region-aware async hop ({@code teleportAsync} on the right Folia
 * region thread), keeping every Bukkit teleport concern out of the application layer.
 *
 * <p>Deliberately not the teleport context's gated player teleport: there is no cooldown, no warmup and no
 * move-cancels rule here, because the caller is an admin inspecting something they just listed. A player-facing
 * teleport goes through the teleport context instead.
 *
 * <p>Fire-and-forget: the method returns {@code void}; the adapter is responsible for landing the player.
 */
public interface DirectTeleporter {

    /** Send {@code who} to {@code destination}, hopping to the destination's region thread first (Folia). */
    void teleport(PlayerRef who, Position destination);
}
