package com.uxplima.uxmessentials.bossbar.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarAnnouncements;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarRenderer;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Draws a joining player's bars at once rather than at the next refresh, draws them again when a world or a game mode
 * a condition may name changes, and forgets a player who left.
 */
@NullMarked
public final class BossBarConnectionListener implements Listener {

    private final BossBarRenderer renderer;
    private final BossBarAnnouncements announcements;
    private final Scheduler scheduler;

    public BossBarConnectionListener(
            BossBarRenderer renderer, BossBarAnnouncements announcements, Scheduler scheduler) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        renderer.renderFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        renderer.forget(player);
        announcements.forget(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        redraw(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        redraw(event.getPlayer());
    }

    private void redraw(Player player) {
        PlayerRef who = BukkitRefs.toRef(player);
        scheduler.onEntity(who, () -> {
            Player live = Bukkit.getPlayer(who.uuid());
            if (live != null) {
                renderer.renderFor(live);
            }
        });
    }
}
