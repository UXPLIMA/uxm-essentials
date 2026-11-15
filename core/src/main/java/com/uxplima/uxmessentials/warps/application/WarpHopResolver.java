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
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * The server-warp side of the shared cross-server hop. A hop's target is the warp's name. On the target the warp is
 * read again and moved to only while it still exists and still lives on this backend, and only onto a destination
 * that passes the safety check a local use passes; the move is the teleport context's immediate arrival, through
 * {@link WarpTeleporter#arrive}, and a landing counts as a visit. The charge goes back through the same
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
    private final WarpSafetyChecker safety;
    private final Permissions permissions;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final String localServer;

    public WarpHopResolver(
            WarpRepository repository,
            WarpTeleporter teleporter,
            Optional<WarpEconomy> economy,
            WarpSafetyChecker safety,
            Permissions permissions,
            Notifier notifier,
            Scheduler scheduler,
            String localServer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.safety = Objects.requireNonNull(safety, "safety");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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
                warp.name().value(), (who, onLanded, onMissed) -> land(who, warp, onLanded, onMissed));
    }

    /**
     * Judge the destination on its own region thread, the only one that may read its blocks, then move the player on
     * theirs. An unsafe destination is refused as a local use would be, unless the player may bypass the check, and
     * the miss gives the charge back. A failure anywhere on the way is a miss too, so the claimed hop always ends in
     * exactly one outcome.
     */
    private void land(PlayerRef who, Warp warp, Runnable onLanded, Runnable onMissed) {
        scheduler.onRegion(warp.location(), () -> {
            boolean safe = isSafe(warp);
            try {
                scheduler.onEntity(who, () -> arriveIfAllowed(who, warp, safe, onLanded, onMissed), onMissed);
            } catch (RuntimeException unscheduled) {
                onMissed.run();
            }
        });
    }

    private void arriveIfAllowed(PlayerRef who, Warp warp, boolean safe, Runnable onLanded, Runnable onMissed) {
        if (!safe && !permissions.has(who, UseWarp.BYPASS_SAFETY)) {
            notifier.send(
                    who,
                    WarpError.UNSAFE_LOCATION.messageKey(),
                    Map.of("warp", warp.name().value()));
            onMissed.run();
            return;
        }
        Runnable landed = () -> {
            onLanded.run();
            countVisit(warp.name());
        };
        teleporter.arrive(who, warp, landed, onMissed);
    }

    private boolean isSafe(Warp warp) {
        try {
            return safety.isSafe(warp.location());
        } catch (RuntimeException unreadable) {
            // A destination that cannot be judged is treated as unsafe: the player is refunded, not dropped into it.
            return false;
        }
    }

    /**
     * Count the landing as a visit, as a local use counts one. A statistic, so a lane that refuses the write (a
     * backend shutting down) loses only the count, and the answer says whether it was queued.
     */
    private boolean countVisit(WarpName name) {
        try {
            scheduler.async(() -> repository.find(name).ifPresent(warp -> repository.save(warp.incrementedVisitors())));
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
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
