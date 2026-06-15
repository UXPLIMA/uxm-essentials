package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramRenderer;
import org.jspecify.annotations.NullMarked;

/**
 * Re-evaluates the permission-gated holograms for a player the moment they join, so they see the ones they hold
 * the node for at once rather than waiting up to a full refresh interval. The shared {@code TextDisplay} entities
 * are visible by default, so {@code ALL} holograms need no per-player call; only {@code PERMISSION} holograms are
 * touched, and the renderer routes each show/hide onto the entity's region thread. A player quitting or changing
 * world is already handled by the uxmLib hologram lifecycle listener, which forgets their viewer entry, so this
 * listener only needs the join hook.
 */
@NullMarked
public final class HologramVisibilityListener implements Listener {

    private final HologramRenderer renderer;

    public HologramVisibilityListener(HologramRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        renderer.recomputeVisibilityFor(event.getPlayer());
    }
}
