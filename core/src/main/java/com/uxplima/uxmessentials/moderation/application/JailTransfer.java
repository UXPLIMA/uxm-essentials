package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.moderation.application.port.JailCells;
import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Puts a jailed player who is connected to this backend into their jail: here when the jail is here, or across the
 * proxy through the shared cross-server hop when the jail was set on another backend. A player who is not connected
 * here is left alone; the backend holding them applies the jail, and every backend applies it again on join.
 *
 * <p>Each attempt reads the sentence again, so a release that lands first (an unjail while the transfer waited for
 * the async lane, or while a retry was pending) stops it, and so does a jail moved to another cell. Who is connected
 * here comes from network presence, a memory read, so nothing here touches the server API off its thread.
 *
 * <p>The hop is of the jail kind: it carries no charge and passes no teleport gate, since the player did not choose
 * it and being jailed must not stop it. When the jail's backend does not answer, the jail is applied here if its world
 * is loaded here (a backend renamed since the jail was set); otherwise, and when a hop fails, the player stays where
 * they are, restricted by the jail gate as on any backend, staff are told through {@link JailHopAlerts}, and the
 * transfer is tried again with a growing delay, so a prisoner dropped to the lobby while the jail's backend restarts
 * is sent back once it is up, without relogging. A player a staff member froze is not sent away either: the hop waits
 * for the freeze to be lifted, through the same retries.
 *
 * <p>With the network off ({@code departures} empty) every jail is applied on this backend, as it always was. The
 * reads run on the async lane, and every move hops to the player's own thread.
 */
public final class JailTransfer {

    /** The first wait before a failed transfer is tried again; each later wait doubles. */
    static final Duration FIRST_RETRY = Duration.ofSeconds(30);

    /** The longest wait between two attempts, so a backend that is down for long is still retried. */
    static final Duration LONGEST_RETRY = Duration.ofMinutes(5);

    private final ModerationRepository repository;
    private final JailLocationStore store;
    private final Sanctions sanctions;
    private final JailCells cells;
    private final NetworkPresence network;
    private final Optional<HopDepartures> departures;
    private final JailHopAlerts alerts;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final Logger log;
    private final Clock clock;
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();

    public JailTransfer(
            ModerationRepository repository,
            JailLocationStore store,
            Sanctions sanctions,
            JailCells cells,
            NetworkPresence network,
            Optional<HopDepartures> departures,
            JailHopAlerts alerts,
            Notifier notifier,
            Scheduler scheduler,
            Logger log,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.store = Objects.requireNonNull(store, "store");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.cells = Objects.requireNonNull(cells, "cells");
        this.network = Objects.requireNonNull(network, "network");
        this.departures = Objects.requireNonNull(departures, "departures");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Put {@code target} into {@code jail}, if they are connected here and still jailed there; any thread. */
    public void send(PlayerRef target, String jail) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(jail, "jail");
        try {
            scheduler.async(() -> route(target.uuid(), Optional.of(jail), false));
        } catch (RuntimeException refused) {
            // A disabling backend refuses async work; the record still holds them and is applied at their next join.
            log.warn(
                    "event=jail_transfer_unscheduled player={} jail={} reason={}",
                    target.uuid(),
                    jail,
                    refused.toString());
        }
    }

    /**
     * Try {@code target}'s transfer again later, after a hop that failed or a jail backend that did not answer. One
     * retry waits per player at a time, and the wait doubles up to {@link #LONGEST_RETRY}; the retries stop once the
     * player is in the cell, released, or gone from this backend.
     */
    public void retryLater(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        UUID player = target.uuid();
        if (!waiting.add(player)) {
            return;
        }
        int attempt = attempts.merge(player, 1, Integer::sum);
        try {
            scheduler.asyncAfter(backoff(attempt), () -> {
                waiting.remove(player);
                route(player, Optional.empty(), true);
            });
        } catch (RuntimeException refused) {
            waiting.remove(player);
            log.warn("event=jail_transfer_retry_unscheduled player={} reason={}", player, refused.toString());
        }
    }

    /** The wait before attempt {@code attempt}: {@link #FIRST_RETRY}, doubled each time, never past the cap. */
    static Duration backoff(int attempt) {
        int doublings = Math.min(Math.max(attempt - 1, 0), 10);
        Duration wait = FIRST_RETRY.multipliedBy(1L << doublings);
        return wait.compareTo(LONGEST_RETRY) > 0 ? LONGEST_RETRY : wait;
    }

    private void route(UUID player, Optional<String> expected, boolean retry) {
        try {
            routeOrThrow(player, expected, retry);
        } catch (RuntimeException failed) {
            log.error("event=jail_transfer_failed player=" + player, failed);
        }
    }

    private void routeOrThrow(UUID player, Optional<String> expected, boolean retry) {
        Optional<PlayerRef> connected = connectedHere(player);
        if (connected.isEmpty()) {
            forget(player);
            return;
        }
        PlayerRef target = connected.get();
        if (!(repository.loadJail(target) instanceof JailState.Active sentence)
                || !sentence.isActiveAt(clock.instant())
                || expected.filter(name -> !name.equalsIgnoreCase(sentence.jail()))
                        .isPresent()) {
            forget(player);
            return;
        }
        Optional<StoredJail> stored = store.find(sentence.jail());
        if (stored.isEmpty()) {
            // A jail from moderation.conf: it has no server and is resolved by the live side.
            forget(player);
            sanctions.sendToJail(target, sentence.jail());
            return;
        }
        StoredJail cell = stored.get();
        if (departures.isEmpty() || !cell.livesElsewhere(network.localServer())) {
            forget(player);
            scheduler.onEntity(target, () -> cells.move(target, cell.location(), () -> {}, () -> {}));
            return;
        }
        String server = cell.knownServer().orElseThrow();
        if (network.isLive(server)) {
            if (sanctions.isFrozen(target)) {
                // A staff member froze them to talk to them here; the transfer waits until the freeze is lifted.
                retryLater(target);
                return;
            }
            HopDeparture departure =
                    new HopDeparture(HopKind.JAIL, cell.name(), server, cell.name(), cell.location(), Optional.empty());
            departures.get().send(target, departure, Optional.empty(), Optional.empty());
            // A hop that fails later starts its own retries; this one counts as a fresh start.
            forget(player);
            return;
        }
        scheduler.onEntity(target, () -> {
            if (cells.hasWorld(cell.location().world())) {
                forget(player);
                log.debug("event=jail_applied_here jail={} server={} reason=server_not_live", cell.name(), server);
                cells.move(target, cell.location(), () -> {}, () -> {});
                return;
            }
            if (!retry) {
                notifier.send(target, ModerationMessageKey.JAIL_HOP_FAILED, Map.of("jail", cell.name()));
            }
            alerts.failedHere(target, cell.name());
            retryLater(target);
        });
    }

    /** The player with this account, when presence has them on this backend. */
    private Optional<PlayerRef> connectedHere(UUID player) {
        return network.find(player)
                .filter(online -> online.server().equals(network.localServer()))
                .map(OnlinePlayer::player);
    }

    private void forget(UUID player) {
        attempts.remove(player);
    }
}
