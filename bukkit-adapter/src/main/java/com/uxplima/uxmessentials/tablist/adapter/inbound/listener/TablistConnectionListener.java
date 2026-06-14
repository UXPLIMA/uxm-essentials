package com.uxplima.uxmessentials.tablist.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistRenderer;
import org.jspecify.annotations.NullMarked;

/**
 * Renders a player's tablist header/footer the moment they join so they do not wait a full refresh interval for the
 * first paint, and clears it on quit so a dropped player leaves no stale header/footer behind. Both events fire on the
 * joining/quitting player's region thread, so reading the live player and touching their tablist is region-local — no
 * scheduler hop is needed here.
 */
@NullMarked
public final class TablistConnectionListener implements Listener {

    private final TablistRenderer renderer;

    public TablistConnectionListener(TablistRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        renderer.renderFor(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        renderer.forget(player);
    }
}
