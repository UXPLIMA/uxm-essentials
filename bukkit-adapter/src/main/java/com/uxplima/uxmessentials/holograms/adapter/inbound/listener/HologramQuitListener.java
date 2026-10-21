package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.holograms.adapter.outbound.PacketHologramView;
import org.jspecify.annotations.NullMarked;

/**
 * Drops what was resolved for a player as they leave. A joiner needs no event: every hologram asks who is near it
 * on each frame, so a player who joins is drawn what they may see within one frame, in their own language.
 */
@NullMarked
public final class HologramQuitListener implements Listener {

    private final PacketHologramView view;

    public HologramQuitListener(PacketHologramView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        view.forget(event.getPlayer().getUniqueId());
    }
}
