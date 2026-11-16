package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopNotice;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * How {@link UsePlayerWarp} hands a warp on another backend to the shared cross-server hop. {@code departures} is
 * empty while the {@code cross-server} sub-group is off; {@link #admit} then refuses every such use before the charge,
 * telling the player the warp is on a server this one does not send players to, so nothing is taken or given back.
 * While the sub-group is on, {@link #admit} runs the jail, combat and cooldown checks of a local teleport.
 *
 * <p>The owner's share is never banked at charge time for such a use: the hop carries a settlement to the warp, so
 * the target pays the owner only once the visitor has landed, and every miss gives the visitor their charge back.
 */
public final class PlayerWarpHops {

    private final Optional<HopDepartures> departures;
    private final HopCharges charges;
    private final PlayerWarpHopResolver resolver;
    private final Notifier notifier;

    public PlayerWarpHops(
            Optional<HopDepartures> departures, HopCharges charges, PlayerWarpHopResolver resolver, Notifier notifier) {
        this.departures = Objects.requireNonNull(departures, "departures");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Whether {@code who} may hop to {@code warp} under the local teleport rules; asked before the charge. With the
     * cross-server sub-group off no hop can start, so the use is refused here, before anything is charged. On false
     * the player has already been told why.
     */
    public boolean admit(PlayerRef who, PlayerWarp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        HopDeparture departure = departureOf(warp);
        if (departures.isEmpty()) {
            notifier.send(
                    who,
                    PlayerwarpsMessageKey.PWARP_CROSS_SERVER_OFF,
                    Map.of("warp", departure.label(), "server", departure.targetServer()));
            return false;
        }
        return departures.get().admit(who, departure);
    }

    /**
     * Send {@code who} to the remote {@code warp}, carrying exactly what the gate {@code charged}. {@link #admit} has
     * already refused a use while the sub-group is off; one that gets here anyway is refunded as unavailable.
     */
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
                warp.ownCooldown());
    }
}
