package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.moderation.application.OnlineMutes;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;

/**
 * Starts and stops keeping a player's mute in memory as they join and leave. The join handler runs first so the read
 * on the async lane starts as early as it can; until it finishes the mute checks ask the database as before.
 */
@NullMarked
public final class OnlineMutesListener implements Listener {

    private final OnlineMutes mutes;

    public OnlineMutesListener(OnlineMutes mutes) {
        this.mutes = Objects.requireNonNull(mutes, "mutes");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        mutes.joined(BukkitRefs.toRef(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        mutes.left(BukkitRefs.toRef(event.getPlayer()));
    }
}
