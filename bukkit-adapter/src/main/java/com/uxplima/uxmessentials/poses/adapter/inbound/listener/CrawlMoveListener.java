package com.uxplima.uxmessentials.poses.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.application.port.CrawlView;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps a crawl running while the crawler walks. A crawl is anchored on no seat, so the ceiling that stops their
 * client standing back up has to travel with them: {@link CrawlView#hold} re-states it at each new position.
 *
 * <p>The handler runs on the player's own region thread, the thread the move event already fires on, so it needs no
 * scheduler hop. It is gated to real movement first (a {@link PlayerMoveEvent} fires on every look and micro-step)
 * and only then looks the session up, which keeps the hot move path cheap for the players who are not crawling.
 *
 * <p>Two environments end a crawl rather than follow it: water, where the swimming pose is the real thing and the
 * ceiling would only get in the way, and flight, where a prone player is not crawling in any meaningful sense.
 */
@NullMarked
public final class CrawlMoveListener implements Listener {

    private final PoseSessions sessions;
    private final CrawlView crawlView;
    private final StopPose stopPose;

    public CrawlMoveListener(PoseSessions sessions, CrawlView crawlView, StopPose stopPose) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.crawlView = Objects.requireNonNull(crawlView, "crawlView");
        this.stopPose = Objects.requireNonNull(stopPose, "stopPose");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (samePosition(from, to)) {
            // A look-only turn: the ceiling has not moved, so skip before any lookup (this is the hot path).
            return;
        }
        Player player = event.getPlayer();
        if (!isCrawling(player)) {
            return;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        if (player.isInWater() || player.isFlying()) {
            stopPose.stop(who);
            return;
        }
        crawlView.hold(who, BukkitRefs.toPosition(to));
    }

    private boolean isCrawling(Player player) {
        Optional<PoseSession> session = sessions.current(BukkitRefs.toRef(player));
        return session.isPresent() && session.get().type() == PoseType.CRAWL;
    }

    /** Whether {@code from} and {@code to} sit at the very same spot, which is every look-only move. */
    private static boolean samePosition(Location from, Location to) {
        return from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ();
    }
}
