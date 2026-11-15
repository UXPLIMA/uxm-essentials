package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopNotice;
import com.uxplima.uxmessentials.shared.application.hop.HopNotices;
import com.uxplima.uxmessentials.shared.application.hop.HopResolution;
import com.uxplima.uxmessentials.shared.application.hop.HopResolver;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * The server-warp side of the shared cross-server hop. A hop's target is the warp's name. On the target the warp is
 * read again and moved to only while it still exists and still lives on this backend; the move is the teleport
 * context's immediate arrival, through {@link WarpTeleporter#arrive}. The charge goes back through the same
 * {@link WarpEconomy} the use charged on. A warp's cost is the server's, so a hop to one never carries a
 * settlement: there is no one to pay on once the player lands.
 */
public final class WarpHopResolver implements HopResolver {

    private static final HopNotices NOTICES = new HopNotices(
            "warp",
            Map.of(
                    HopNotice.SENDING, WarpsMessageKey.WARP_CROSS_SERVER_SENDING,
                    HopNotice.ARRIVED, WarpsMessageKey.WARP_CROSS_SERVER_ARRIVED,
                    HopNotice.TIMED_OUT, WarpsMessageKey.WARP_CROSS_SERVER_TIMED_OUT,
                    HopNotice.MISROUTED, WarpsMessageKey.WARP_CROSS_SERVER_MISROUTED,
                    HopNotice.FAILED, WarpsMessageKey.WARP_CROSS_SERVER_FAILED,
                    HopNotice.UNAVAILABLE, WarpsMessageKey.WARP_CROSS_SERVER_UNAVAILABLE,
                    HopNotice.REFUNDED, WarpsMessageKey.WARP_CROSS_SERVER_REFUNDED));

    private final WarpRepository repository;
    private final WarpTeleporter teleporter;
    private final Optional<WarpEconomy> economy;
    private final String localServer;

    public WarpHopResolver(
            WarpRepository repository, WarpTeleporter teleporter, Optional<WarpEconomy> economy, String localServer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.localServer = Objects.requireNonNull(localServer, "localServer");
    }

    @Override
    public HopKind kind() {
        return HopKind.WARP;
    }

    @Override
    public HopNotices notices() {
        return NOTICES;
    }

    @Override
    public String label(PendingHop hop) {
        // The target is the name the player typed, so it reads right even for a warp deleted since.
        return hop.target();
    }

    @Override
    public HopResolution resolve(PendingHop hop) {
        Optional<Warp> found = repository.find(WarpName.of(hop.target()));
        if (found.isEmpty() || !isHere(found.get())) {
            return HopResolution.unreachable(hop.target());
        }
        Warp warp = found.get();
        return HopResolution.landsAt(
                warp.name().value(), (who, onLanded, onMissed) -> teleporter.arrive(who, warp, onLanded, onMissed));
    }

    @Override
    public boolean refund(PlayerRef who, HopCharge charge) {
        return economy.isPresent() && economy.get().refund(who, charge.amount(), charge.currencyId());
    }

    @Override
    public boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge) {
        // A warp hop is sent without a settlement, so this is never asked; answering false hands the charge back
        // should a row ever carry one.
        return false;
    }

    /** A warp moved to another backend since the hop was sent can no longer be landed at from here. */
    private boolean isHere(Warp warp) {
        return warp.server().map(localServer::equals).orElse(false);
    }
}
