package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerwarps.application.CrossServerCharges;
import com.uxplima.uxmessentials.playerwarps.application.CrossServerWatchdog;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.port.CrossServerTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link CrossServerTeleport} implementation: the send half of a cross-server player-warp hop. Given a warp
 * the access gate has admitted (and, for a priced non-member, already charged), it records the intent in the
 * shared pending-teleport store through the {@link CrossServerWatchdog} and asks the proxy to move the player to
 * the warp's backend through the existing {@link ServerConnector}. The target backend's join handler then claims
 * the row and completes the local hop. The connect cannot report whether the proxy acted on it: with no proxy in
 * front, or a target name the proxy does not know, the frame is dropped silently. So the watchdog refunds any
 * request no backend claimed within the request TTL, and the {@code isAvailable()} check only catches a channel
 * this plugin failed to register.
 *
 * <p>The gate runs off the tick thread, so the watchdog's store write is safe here; the {@code connect} plugin
 * message rides the player's own connection, so it is marshalled back onto the player's entity region through the
 * {@link Scheduler} port (Folia-safe) and skipped if they logged off in between, which leaves the request to the
 * watchdog. Every refund goes through {@link CrossServerCharges}, which credits through the same economy seam the
 * charge used and tells the player once the credit landed.
 */
@NullMarked
public final class BukkitCrossServerTeleport implements CrossServerTeleport {

    private final ServerConnector connector;
    private final CrossServerWatchdog watchdog;
    private final CrossServerCharges charges;
    private final Scheduler scheduler;
    private final Notifier notifier;
    private final String localServerId;
    private final Clock clock;

    public BukkitCrossServerTeleport(
            ServerConnector connector,
            CrossServerWatchdog watchdog,
            CrossServerCharges charges,
            Scheduler scheduler,
            Notifier notifier,
            String localServerId,
            Clock clock) {
        this.connector = Objects.requireNonNull(connector, "connector");
        this.watchdog = Objects.requireNonNull(watchdog, "watchdog");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.localServerId = Objects.requireNonNull(localServerId, "localServerId");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void send(PlayerRef who, PlayerWarp warp, Optional<WarpCost> charged) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(charged, "charged");
        String target = warp.serverId().orElseThrow();
        String name = warp.name().value();
        if (!connector.isAvailable()) {
            unreachable(who, charged, name, target);
            return;
        }
        PendingTeleport pending = new PendingTeleport(
                who.uuid(), warp.id().orElseThrow(), target, localServerId, clock.instant(), charged, true);
        // An untracked request has already been refunded by the watchdog, so the player must not be sent.
        if (!watchdog.track(who, pending, name)) {
            return;
        }
        notifier.send(who, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_SENDING, Map.of("warp", name, "server", target));
        scheduler.onEntity(who, () -> connect(who.uuid(), target));
    }

    private void unreachable(PlayerRef who, Optional<WarpCost> charged, String name, String target) {
        charges.fail(
                who, charged, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_UNAVAILABLE, Map.of("server", target), name);
    }

    /** On the player's region thread: connect the still-online player to {@code target}, or skip if they left. */
    private void connect(UUID player, String target) {
        Player online = Bukkit.getPlayer(player);
        if (online != null && online.isOnline()) {
            connector.connect(online, target);
        }
    }
}
