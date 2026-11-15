package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopNotice;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * How {@link UsePlayerWarp} hands a warp on another backend to the shared cross-server hop. {@code departures} is
 * empty while the {@code cross-server} sub-group is off; the gate then refuses nobody here, and a use that was
 * charged is told the warp is unreachable and refunded, since the player never moves.
 *
 * <p>The owner's share is never banked at charge time for such a use: the hop carries a settlement to the warp, so
 * the target pays the owner only once the visitor has landed, and every miss gives the visitor their charge back.
 */
public final class PlayerWarpHops {

    private final Optional<HopDepartures> departures;
    private final HopCharges charges;
    private final PlayerWarpHopResolver resolver;

    public PlayerWarpHops(Optional<HopDepartures> departures, HopCharges charges, PlayerWarpHopResolver resolver) {
        this.departures = Objects.requireNonNull(departures, "departures");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** Whether {@code who} may hop to {@code warp} under the local teleport rules; asked before the charge. */
    public boolean admit(PlayerRef who, PlayerWarp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        return departures.map(route -> route.admit(who, departureOf(warp))).orElse(true);
    }

    /** Send {@code who} to the remote {@code warp}, carrying exactly what the gate {@code charged}. */
    public void send(PlayerRef who, PlayerWarp warp, Optional<WarpCost> charged) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(charged, "charged");
        Optional<HopCharge> charge = charged.map(cost -> new HopCharge(cost.amount(), cost.currencyId()));
        HopDeparture departure = departureOf(warp);
        if (departures.isPresent()) {
            departures.get().send(who, departure, charge, Optional.of(new HopSettlement(departure.target())));
            return;
        }
        charges.fail(
                resolver,
                who,
                charge,
                HopNotice.UNAVAILABLE,
                Map.of("server", departure.targetServer()),
                departure.label());
    }

    /** The hop to {@code warp}: its id as the reference, and its own cooldown so the gate and landing agree. */
    static HopDeparture departureOf(PlayerWarp warp) {
        return new HopDeparture(
                HopKind.PLAYER_WARP,
                Long.toString(warp.id().orElseThrow().value()),
                warp.serverId().orElseThrow(),
                warp.name().value(),
                warp.location(),
                warp.timing().cooldown());
    }
}
