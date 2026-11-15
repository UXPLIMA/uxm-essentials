package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.JailCells;
import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
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

/**
 * The jail side of the shared cross-server hop. A hop's target is the jail's name; on the target the jail is read
 * again and the player is moved into it only while it still exists and does not live on another backend. A jail
 * hop carries no money, so there is never anything to refund or settle. A hop that ends without moving the player
 * leaves them jailed where they are, and staff hear about it through {@link JailHopAlerts}.
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
                    // Never sent: a jail hop is never charged, so it is never refunded.
                    HopNotice.REFUNDED, ModerationMessageKey.JAIL_HOP_FAILED));

    private final JailLocationStore store;
    private final JailCells cells;
    private final JailHopAlerts alerts;
    private final String localServer;

    public JailHopResolver(JailLocationStore store, JailCells cells, JailHopAlerts alerts, String localServer) {
        this.store = Objects.requireNonNull(store, "store");
        this.cells = Objects.requireNonNull(cells, "cells");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.localServer = Objects.requireNonNull(localServer, "localServer");
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
        alerts.failedHere(who, label);
    }
}
