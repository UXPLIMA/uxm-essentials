package com.uxplima.uxmessentials.shared.application.hop.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;

/**
 * One in-flight cross-server hop: the origin backend took any charge, recorded this row in the network-shared
 * store and handed the player to the proxy; the target backend reads it on join and moves the player, or the
 * charge goes back. A player heads to one backend at a time, so there is at most one pending hop per player,
 * whatever its kind.
 *
 * @param player the player being routed
 * @param kind what the hop is heading to, which picks the resolver on the target
 * @param target the kind-specific reference the target resolves (for a player warp, its surrogate id)
 * @param targetServer the network name of the backend the destination lives on
 * @param originServer the network name of the backend that recorded the hop
 * @param requestedAt when the origin recorded the hop, for the expiry check
 * @param charge what the origin debited, to give back if the hop never lands; empty when nothing was charged
 * @param settlement where the charge goes once the player lands; empty when the origin already settled it
 */
public record PendingHop(
        UUID player,
        HopKind kind,
        String target,
        String targetServer,
        String originServer,
        Instant requestedAt,
        Optional<HopCharge> charge,
        Optional<HopSettlement> settlement) {

    public PendingHop {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(settlement, "settlement");
    }
}
