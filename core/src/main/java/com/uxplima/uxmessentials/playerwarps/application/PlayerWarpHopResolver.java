package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopNotice;
import com.uxplima.uxmessentials.shared.application.hop.HopNotices;
import com.uxplima.uxmessentials.shared.application.hop.HopResolution;
import com.uxplima.uxmessentials.shared.application.hop.HopResolver;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The player-warp side of the shared cross-server hop. A hop's target and settlement payee are both the warp's
 * surrogate id. On the target the warp is read again and moved to only while it is still ACTIVE and still lives on
 * this backend; the move is the teleport context's immediate arrival, through {@link PlayerWarpTeleporter#arrive}.
 * The charge goes back through the same {@link PlayerWarpEconomy} the gate charged on, and a landed hop banks the
 * owner's share in the warp's vault.
 */
public final class PlayerWarpHopResolver implements HopResolver {

    private static final HopNotices NOTICES = new HopNotices(
            "warp",
            Map.of(
                    HopNotice.SENDING, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_SENDING,
                    HopNotice.ARRIVED, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_ARRIVED,
                    HopNotice.TIMED_OUT, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_TIMED_OUT,
                    HopNotice.MISROUTED, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_MISROUTED,
                    HopNotice.FAILED, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_FAILED,
                    HopNotice.UNAVAILABLE, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_UNAVAILABLE,
                    HopNotice.REFUNDED, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_REFUNDED));

    private final PlayerWarpRepository repository;
    private final PlayerWarpTeleporter teleporter;
    private final Optional<PlayerWarpEconomy> economy;
    private final String localServerId;

    public PlayerWarpHopResolver(
            PlayerWarpRepository repository,
            PlayerWarpTeleporter teleporter,
            Optional<PlayerWarpEconomy> economy,
            String localServerId) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.localServerId = Objects.requireNonNull(localServerId, "localServerId");
    }

    @Override
    public HopKind kind() {
        return HopKind.PLAYER_WARP;
    }

    @Override
    public HopNotices notices() {
        return NOTICES;
    }

    @Override
    public String label(PendingHop hop) {
        return label(repository.findById(warpOf(hop.target())), hop);
    }

    @Override
    public HopResolution resolve(PendingHop hop) {
        Optional<PlayerWarp> resolved = repository.findById(warpOf(hop.target()));
        if (!isArrivable(resolved)) {
            return HopResolution.unreachable(label(resolved, hop));
        }
        PlayerWarp warp = resolved.orElseThrow();
        return HopResolution.landsAt(
                warp.name().value(), (who, onLanded, onMissed) -> teleporter.arrive(who, warp, onLanded, onMissed));
    }

    @Override
    public boolean refund(PlayerRef who, HopCharge charge) {
        return economy.isPresent()
                && economy.get()
                        .refund(who, charge.amount(), charge.currencyId())
                        .isOk();
    }

    @Override
    public boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge) {
        return economy.isPresent()
                && economy.get()
                        .accrue(warpOf(settlement.payee()), charge.amount(), charge.currencyId())
                        .isOk();
    }

    /** A warp is arrivable only if it still exists, is ACTIVE, and lives on this backend as of the read. */
    private boolean isArrivable(Optional<PlayerWarp> warp) {
        return warp.filter(w -> w.status() == WarpStatus.ACTIVE)
                .flatMap(PlayerWarp::serverId)
                .map(localServerId::equals)
                .orElse(false);
    }

    /** The warp name when known, else its surrogate id, which is all a deleted warp leaves. */
    private static String label(Optional<PlayerWarp> warp, PendingHop hop) {
        return warp.map(w -> w.name().value()).orElse("#" + hop.target());
    }

    private static PlayerWarpId warpOf(String reference) {
        return PlayerWarpId.of(Long.parseLong(reference));
    }
}
