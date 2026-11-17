package com.uxplima.uxmessentials.shared.adapter.inbound.notice;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.DeferredPlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Shows a joining player the lines kept for them while they were away, such as being paid. It runs at MONITOR, after
 * the join messages, and reads the database on the async lane.
 */
public final class KeptNoticeJoinListener implements Listener {

    private final DeferredPlayerNotices notices;
    private final Scheduler scheduler;

    public KeptNoticeJoinListener(DeferredPlayerNotices notices, Scheduler scheduler) {
        this.notices = Objects.requireNonNull(notices, "notices");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef joiner = BukkitRefs.toRef(event.getPlayer());
        scheduler.async(() -> notices.deliver(joiner));
    }
}
