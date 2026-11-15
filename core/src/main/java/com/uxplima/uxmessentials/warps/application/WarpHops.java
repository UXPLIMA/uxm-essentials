package com.uxplima.uxmessentials.warps.application;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * How {@link UseWarp} hands a warp on another backend to the shared cross-server hop. {@code departures} is empty
 * while the warps {@code cross-server} block is off; a warp is then used on this backend whatever server it was
 * set on, as it always was, so turning the block on is what changes behaviour.
 */
public final class WarpHops {

    private final Optional<HopDepartures> departures;
    private final String localServer;

    public WarpHops(Optional<HopDepartures> departures, String localServer) {
        this.departures = Objects.requireNonNull(departures, "departures");
        this.localServer = Objects.requireNonNull(localServer, "localServer");
    }

    /** The route that is never taken: every warp is used on this backend. */
    public static WarpHops local(String localServer) {
        return new WarpHops(Optional.empty(), localServer);
    }

    /** True when {@code warp} is used by sending the player to the backend it was set on. */
    public boolean routes(Warp warp) {
        Objects.requireNonNull(warp, "warp");
        return departures.isPresent() && warp.livesElsewhere(localServer);
    }

    /**
     * Whether {@code who} may hop to the remote {@code warp} under the local teleport rules; asked before the charge,
     * so a refused hop costs nothing. On false the player has already been told why.
     */
    public boolean admit(PlayerRef who, Warp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        return departures.orElseThrow().admit(who, departureOf(warp));
    }

    /** Send {@code who} to the remote {@code warp}, carrying exactly what was {@code charged} so a miss refunds it. */
    public void send(PlayerRef who, Warp warp, Optional<WarpCost> charged) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(charged, "charged");
        Optional<HopCharge> charge = charged.map(cost -> new HopCharge(cost.amount(), cost.currencyId()));
        departures.orElseThrow().send(who, departureOf(warp), charge, Optional.empty());
    }

    /**
     * The hop to {@code warp}: its name as the reference, and its own cooldown so the origin's gate checks the key
     * the landing stamps.
     */
    static HopDeparture departureOf(Warp warp) {
        Optional<Duration> cooldown =
                warp.cooldownOverrideSeconds().map(seconds -> Duration.ofMillis((long) (seconds * 1000)));
        return new HopDeparture(
                HopKind.WARP,
                warp.name().value(),
                warp.server().orElseThrow(),
                warp.name().value(),
                warp.location(),
                cooldown);
    }
}
