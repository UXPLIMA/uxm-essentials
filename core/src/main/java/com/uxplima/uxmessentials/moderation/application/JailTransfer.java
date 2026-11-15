package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Puts a jailed player who is connected to this backend into their jail: here when the jail is here, or across the
 * proxy through the shared cross-server hop when the jail was set on another backend. A player who is not connected
 * here is left alone; the backend holding them applies the jail, and every backend applies it again on join.
 *
 * <p>The hop is of the jail kind: it carries no charge and passes no teleport gate, since the player did not choose
 * it and being jailed must not stop it. A hop that cannot start because the jail's backend is not running, or that
 * fails later, leaves the player jailed where they are and tells staff through {@link JailHopAlerts}.
 *
 * <p>With the network off ({@code departures} empty) every jail is applied on this backend, as it always was. The
 * jail's server is read from the shared store, so the routing runs on the async lane, and the local move hops to the
 * player's own thread inside {@link Sanctions}.
 */
public final class JailTransfer {

    private final JailLocationStore store;
    private final Sanctions sanctions;
    private final PlayerLookup here;
    private final NetworkPresence network;
    private final Optional<HopDepartures> departures;
    private final JailHopAlerts alerts;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final Logger log;

    public JailTransfer(
            JailLocationStore store,
            Sanctions sanctions,
            PlayerLookup here,
            NetworkPresence network,
            Optional<HopDepartures> departures,
            JailHopAlerts alerts,
            Notifier notifier,
            Scheduler scheduler,
            Logger log) {
        this.store = Objects.requireNonNull(store, "store");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.here = Objects.requireNonNull(here, "here");
        this.network = Objects.requireNonNull(network, "network");
        this.departures = Objects.requireNonNull(departures, "departures");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Put {@code target} into {@code jail} if they are connected here; callable from any thread. */
    public void send(PlayerRef target, String jail) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(jail, "jail");
        try {
            scheduler.async(() -> route(target, jail));
        } catch (RuntimeException refused) {
            // A disabling backend refuses async work; the record still holds them and is applied at their next join.
            log.warn(
                    "event=jail_transfer_unscheduled player={} jail={} reason={}",
                    target.uuid(),
                    jail,
                    refused.toString());
        }
    }

    private void route(PlayerRef target, String jail) {
        if (!here.isOnline(target.uuid())) {
            return;
        }
        Optional<StoredJail> stored;
        try {
            stored = store.find(jail);
        } catch (RuntimeException unreadable) {
            log.error("event=jail_transfer_failed player=" + target.uuid() + " jail=" + jail, unreadable);
            return;
        }
        if (departures.isEmpty() || stored.isEmpty() || !stored.get().livesElsewhere(network.localServer())) {
            sanctions.sendToJail(target, jail);
            return;
        }
        StoredJail cell = stored.get();
        String server = cell.knownServer().orElseThrow();
        if (!network.isLive(server)) {
            notifier.send(target, ModerationMessageKey.JAIL_HOP_FAILED, Map.of("jail", cell.name()));
            alerts.failedHere(target, cell.name());
            return;
        }
        HopDeparture departure =
                new HopDeparture(HopKind.JAIL, cell.name(), server, cell.name(), cell.location(), Optional.empty());
        departures.get().send(target, departure, Optional.empty(), Optional.empty());
    }
}
