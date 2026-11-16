package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopLanding;
import com.uxplima.uxmessentials.shared.application.hop.HopNotice;
import com.uxplima.uxmessentials.shared.application.hop.HopNotices;
import com.uxplima.uxmessentials.shared.application.hop.HopResolution;
import com.uxplima.uxmessentials.shared.application.hop.HopResolver;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;

/**
 * The player-warp side of the shared cross-server hop. A hop's target and settlement payee are both the warp's
 * surrogate id. On the target the warp is read again and moved to only while it is still ACTIVE and still lives on
 * this backend, and only onto a destination that passes the safety check a local use passes; the origin cannot judge
 * it, since it does not hold the warp's world. The move is the teleport context's immediate arrival, through
 * {@link PlayerWarpTeleporter#arrive}.
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
    private final WarpSafetyChecker safety;
    private final Permissions permissions;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final String localServerId;

    public PlayerWarpHopResolver(
            PlayerWarpRepository repository,
            PlayerWarpTeleporter teleporter,
            Optional<PlayerWarpEconomy> economy,
            WarpSafetyChecker safety,
            Permissions permissions,
            Notifier notifier,
            Scheduler scheduler,
            String localServerId) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.safety = Objects.requireNonNull(safety, "safety");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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
                warp.name().value(), (who, onLanded, onMissed) -> land(who, warp, onLanded, onMissed));
    }

    /**
     * Judge the destination on its own region thread, the only one that may read its blocks, then move the player on
     * theirs. An unsafe destination is refused as a local use would be, unless the player may bypass the check, and
     * the miss gives the charge back while the owner stays unpaid. The hop is already claimed by now, so every way the
     * move can fail to happen is a miss: a world that is not loaded here or a backend that is stopping (the region task
     * can never run), a player who left, or a failure on the way. The claimed hop always ends in exactly one outcome.
     */
    private void land(PlayerRef who, PlayerWarp warp, Runnable onLanded, Runnable onMissed) {
        Runnable judge = () -> {
            boolean safe = isSafe(warp);
            scheduler.onEntity(
                    who,
                    HopLanding.missOnFailure(() -> arriveIfAllowed(who, warp, safe, onLanded, onMissed), onMissed),
                    onMissed);
        };
        scheduler.onRegion(warp.location(), HopLanding.missOnFailure(judge, onMissed), onMissed);
    }

    private void arriveIfAllowed(PlayerRef who, PlayerWarp warp, boolean safe, Runnable onLanded, Runnable onMissed) {
        if (!safe && !permissions.has(who, DestinationPermissions.PWARP_BYPASS_SAFETY)) {
            notifier.send(
                    who,
                    PlayerWarpError.UNSAFE_LOCATION.messageKey(),
                    Map.of("warp", warp.name().value()));
            onMissed.run();
            return;
        }
        teleporter.arrive(who, warp, onLanded, onMissed);
    }

    private boolean isSafe(PlayerWarp warp) {
        try {
            return safety.isSafe(warp.location());
        } catch (RuntimeException unreadable) {
            // A destination that cannot be judged is treated as unsafe: the player is refunded, not dropped into it.
            return false;
        }
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
