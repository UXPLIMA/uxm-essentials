package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.moderation.application.port.JailSync;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Tells staff that a jailed player could not be taken to their jail on another backend. The player stays jailed: the
 * record in the shared database is what holds them, and the transfer is tried again, so the line is for staff to know
 * the player is not in the cell yet. The staff member who ran the jail may be on any backend, so a failure seen here
 * is told to the staff here and announced to every peer, and one a peer saw is told here once, however many copies
 * of its report arrive.
 *
 * <p>A prisoner who relogs, and every retry while the jail's backend is down, fails the same way again, so a player
 * and jail are reported at most once per {@link #QUIET} on each backend; a repeat inside that window is neither told
 * nor announced.
 */
public final class JailHopAlerts {

    /** How long a reported player and jail stay quiet: long enough to end relog spam, short enough to notice. */
    static final Duration QUIET = Duration.ofMinutes(5);

    private final SanctionBroadcast staff;
    private final JailSync sync;
    private final Clock clock;
    private final RecentIds reports = new RecentIds();
    private final Map<String, Instant> lastTold = new ConcurrentHashMap<>();

    public JailHopAlerts(SanctionBroadcast staff, JailSync sync, Clock clock) {
        this.staff = Objects.requireNonNull(staff, "staff");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** {@code who} could not be taken to {@code jail}, seen on this backend. */
    public void failedHere(PlayerRef who, String jail) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(jail, "jail");
        if (due(who.uuid(), jail)) {
            announce(who.name(), jail);
            sync.hopFailed(who, jail);
        }
    }

    /** A peer could not take {@code target} to {@code jail}; {@code report} tells the copies of its report apart. */
    public void failedOnPeer(UUID report, UUID target, String playerName, String jail) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(jail, "jail");
        if (reports.firstTime(report) && due(target, jail)) {
            announce(playerName, jail);
        }
    }

    /** Whether this player and jail may be reported now; counts as reported from now on when it may. */
    private boolean due(UUID player, String jail) {
        Instant now = clock.instant();
        // Entries past the window are dropped as new ones arrive, so the map holds only the last few minutes.
        lastTold.values().removeIf(told -> !now.isBefore(told.plus(QUIET)));
        return lastTold.putIfAbsent(player + "/" + jail.toLowerCase(Locale.ROOT), now) == null;
    }

    private void announce(String playerName, String jail) {
        staff.announce(ModerationMessageKey.JAIL_HOP_STAFF_FAILED, Map.of("player", playerName, "jail", jail));
    }
}
