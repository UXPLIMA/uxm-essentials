package com.uxplima.uxmessentials.nametags.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.nametags.adapter.outbound.PacketNametagPresenter;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps a wearer's nametag in step with the connection lifecycle: show it on join so it appears at once rather than
 * after a full reconcile interval, remove it on quit so a dropped player leaves no orphan nametag, and
 * remove-then-show on a world change so the packet renderer re-homes the line stack in the new world's region.
 *
 * <p>The join and world-change handlers hop onto the player's entity thread through the {@link Scheduler} port before
 * touching the live player or starting the lib's per-wearer refresh task (Folia). The quit handler only drops a tracked
 * handle and sends remove packets, which is channel I/O safe from any thread, so it needs no hop here.
 */
@NullMarked
public final class NametagLifecycleListener implements Listener {

    private final PacketNametagPresenter presenter;
    private final Scheduler scheduler;

    public NametagLifecycleListener(PacketNametagPresenter presenter, Scheduler scheduler) {
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.onEntity(BukkitRefs.toRef(player), () -> presenter.show(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        presenter.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // The line stack lived in the previous world's region; remove it, then re-show in the new world's region on
        // the player's entity thread (the lib's refresh task is an entityTimer bound to the player).
        presenter.remove(player.getUniqueId());
        scheduler.onEntity(BukkitRefs.toRef(player), () -> presenter.show(player));
    }
}
