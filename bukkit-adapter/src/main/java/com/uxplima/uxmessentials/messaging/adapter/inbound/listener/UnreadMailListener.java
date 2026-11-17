package com.uxplima.uxmessentials.messaging.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.messaging.application.UnreadMailNotice;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/** Tells a joining player how much unread mail waits for them, counted on the async lane. */
@NullMarked
public final class UnreadMailListener implements Listener {

    private final UnreadMailNotice notice;
    private final Scheduler scheduler;

    public UnreadMailListener(UnreadMailNotice notice, Scheduler scheduler) {
        this.notice = Objects.requireNonNull(notice, "notice");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Monitor: the notice only reads the join, after every other listener has acted on it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef player = BukkitRefs.toRef(event.getPlayer());
        scheduler.async(() -> notice.joined(player));
    }
}
