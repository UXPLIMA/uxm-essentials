package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.JailSync;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Tells staff that a jailed player could not be taken to their jail on another backend. The player stays jailed: the
 * record in the shared database is what holds them, and it is applied again at their next join, so the line is for
 * staff to know the player is not in the cell yet. The staff member who ran the jail may be on any backend, so a
 * failure seen here is told to the staff here and announced to every peer, and one a peer saw is told here once,
 * however many copies of its report arrive.
 */
public final class JailHopAlerts {

    private final SanctionBroadcast staff;
    private final JailSync sync;
    private final RecentIds reports = new RecentIds();

    public JailHopAlerts(SanctionBroadcast staff, JailSync sync) {
        this.staff = Objects.requireNonNull(staff, "staff");
        this.sync = Objects.requireNonNull(sync, "sync");
    }

    /** {@code who} could not be taken to {@code jail}, seen on this backend. */
    public void failedHere(PlayerRef who, String jail) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(jail, "jail");
        announce(who.name(), jail);
        sync.hopFailed(who, jail);
    }

    /** A peer could not take {@code playerName} to {@code jail}; {@code report} tells its copies apart. */
    public void failedOnPeer(UUID report, String playerName, String jail) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(jail, "jail");
        if (reports.firstTime(report)) {
            announce(playerName, jail);
        }
    }

    private void announce(String playerName, String jail) {
        staff.announce(ModerationMessageKey.JAIL_HOP_STAFF_FAILED, Map.of("player", playerName, "jail", jail));
    }
}
