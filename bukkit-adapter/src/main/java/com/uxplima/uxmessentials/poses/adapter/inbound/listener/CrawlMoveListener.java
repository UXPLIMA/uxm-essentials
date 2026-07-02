package com.uxplima.uxmessentials.poses.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import com.uxplima.uxmessentials.poses.application.CrawlSessions;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.port.CrawlView;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Makes the crawl fake block follow the player as they walk. A crawl is not anchored on a seat — the player moves
 * around while prone — so as they cross into a new block the fake block that holds them crawling must move with
 * them: the block just left is restored to reality and a new fake block is shown above the new head. Both packets
 * go to the one player on their own region thread (the thread this event already fires on), so no scheduler hop is
 * needed.
 *
 * <p>The handler is gated to real movement: a {@link PlayerMoveEvent} fires on every look and micro-step, so it
 * first rejects any move that does not cross a block boundary (a look-only or sub-block jitter carries the same
 * block coordinates), then ignores anyone who is not crawling. Only a genuine block change by a crawler reaches the
 * follow work, which keeps the listener cheap on the hot move path.
 */
@NullMarked
public final class CrawlMoveListener implements Listener {

    private final PoseSessions sessions;
    private final CrawlSessions crawlSessions;
    private final CrawlView crawlView;

    public CrawlMoveListener(PoseSessions sessions, CrawlSessions crawlSessions, CrawlView crawlView) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.crawlSessions = Objects.requireNonNull(crawlSessions, "crawlSessions");
        this.crawlView = Objects.requireNonNull(crawlView, "crawlView");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (sameBlock(from, to)) {
            // A look-only turn or a sub-block jitter — no new head to fake, so skip before any lookup (hot path).
            return;
        }
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        Optional<PoseSession> session = sessions.current(who);
        if (session.isEmpty() || session.get().type() != PoseType.CRAWL) {
            return;
        }
        follow(who, to);
    }

    /** Restore the block the crawler just left and fake a new one above their new head, tracking the new position. */
    private void follow(PlayerRef who, Location to) {
        Position feet = BukkitRefs.toPosition(to);
        Position newHead = CrawlSessions.headBlockAbove(feet);
        Optional<Position> oldHead = crawlSessions.current(who);
        if (oldHead.isPresent() && oldHead.get().sameBlock(newHead)) {
            // Moved within the same block column (only Y-into-the-same-cell rounding) — the head block is unchanged.
            return;
        }
        oldHead.ifPresent(old -> crawlView.restoreRealBlock(who, old));
        crawlView.showFakeBlockAbove(who, newHead);
        crawlSessions.track(who, newHead);
    }

    /** Whether {@code from} and {@code to} resolve to the same block cell — a look-only or sub-block move. */
    private static boolean sameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }
}
