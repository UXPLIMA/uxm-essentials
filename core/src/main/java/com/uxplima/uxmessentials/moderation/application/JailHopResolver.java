package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.JailCells;
import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopNotice;
import com.uxplima.uxmessentials.shared.application.hop.HopNotices;
import com.uxplima.uxmessentials.shared.application.hop.HopResolution;
import com.uxplima.uxmessentials.shared.application.hop.HopResolver;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;

/**
 * The jail side of the shared cross-server hop. A hop's target is the jail's name; on the target the jail is read
 * again and the player is moved into it only while it still exists and does not live on another backend. A player
 * released, or jailed somewhere else, while on the way is not moved, and nobody is told the hop failed. A jail hop
 * carries no money, so there is never anything to refund or settle.
 *
 * <p>A hop that ends without moving the player leaves them jailed where they are: staff hear about it through
 * {@link JailHopAlerts} and the backend holding the player tries again through {@link JailTransfer}. A misrouted hop
 * is the one exception: the backend the player reached applies the jail again on join, so it is not reported. A hop
 * turned away because another move of the player's is still under way is retried without a staff alert, since
 * nothing is broken.
 */
public final class JailHopResolver implements HopResolver {

    private static final HopNotices NOTICES = new HopNotices(
            "jail",
            Map.of(
                    HopNotice.SENDING, ModerationMessageKey.JAIL_HOP_SENDING,
                    HopNotice.ARRIVED, ModerationMessageKey.JAIL_HOP_ARRIVED,
                    HopNotice.TIMED_OUT, ModerationMessageKey.JAIL_HOP_FAILED,
                    HopNotice.MISROUTED, ModerationMessageKey.JAIL_HOP_FAILED,
                    HopNotice.FAILED, ModerationMessageKey.JAIL_HOP_FAILED,
                    HopNotice.UNAVAILABLE, ModerationMessageKey.JAIL_HOP_FAILED,
                    // The prisoner did not ask to move, so the line gives no advice to try again; the transfer retries.
                    HopNotice.BUSY, SharedMessageKey.COMMON_HOP_BUSY_ORDERED,
                    // Never sent: a jail hop is never charged, so it is never refunded.
                    HopNotice.REFUNDED, ModerationMessageKey.JAIL_HOP_FAILED));

    private final ModerationRepository repository;
    private final JailLocationStore store;
    private final JailCells cells;
    private final JailHopAlerts alerts;
    private final JailTransfer transfer;
    private final String localServer;
    private final Clock clock;

    public JailHopResolver(
            ModerationRepository repository,
            JailLocationStore store,
            JailCells cells,
            JailHopAlerts alerts,
            JailTransfer transfer,
            String localServer,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.store = Objects.requireNonNull(store, "store");
        this.cells = Objects.requireNonNull(cells, "cells");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.transfer = Objects.requireNonNull(transfer, "transfer");
        this.localServer = Objects.requireNonNull(localServer, "localServer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public HopKind kind() {
        return HopKind.JAIL;
    }

    @Override
    public HopNotices notices() {
        return NOTICES;
    }

    @Override
    public String label(PendingHop hop) {
        return hop.target();
    }

    @Override
    public HopResolution resolve(PendingHop hop) {
        if (!stillJailedIn(new PlayerRef(hop.player(), hop.player().toString()), hop.target())) {
            return HopResolution.withdrawn(hop.target());
        }
        Optional<StoredJail> found = store.find(hop.target());
        if (found.isEmpty() || found.get().livesElsewhere(localServer)) {
            return HopResolution.unreachable(hop.target());
        }
        StoredJail cell = found.get();
        // The arrival runs the landing on the player's own thread, which is where the move has to start.
        return HopResolution.landsAt(
                cell.name(), (who, onLanded, onMissed) -> cells.move(who, cell.location(), onLanded, onMissed));
    }

    @Override
    public boolean refund(PlayerRef who, HopCharge charge) {
        // A jail hop is sent without a charge, so this is never asked; false leaves any stray amount on the log.
        return false;
    }

    @Override
    public boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge) {
        return false;
    }

    @Override
    public void failed(PlayerRef who, String label, HopNotice reason) {
        if (reason == HopNotice.MISROUTED || !stillJailedIn(who, label)) {
            return;
        }
        // A hop turned away because another move of the player's is still under way is not a broken jail, so staff
        // are not alerted; the transfer is only tried again once that move has ended.
        if (reason != HopNotice.BUSY) {
            alerts.failedHere(who, label);
        }
        transfer.retryLater(who);
    }

    private boolean stillJailedIn(PlayerRef who, String jail) {
        return repository.loadJail(who) instanceof JailState.Active sentence
                && sentence.isActiveAt(clock.instant())
                && StoredJail.normalise(sentence.jail()).equals(StoredJail.normalise(jail));
    }
}
