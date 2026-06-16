package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramRenderer;
import org.jspecify.annotations.NullMarked;

/**
 * Re-evaluates the per-player holograms for a player the moment they join, so they see what they qualify for at
 * once rather than waiting up to a full refresh interval. The shared {@code TextDisplay} entities are visible by
 * default, so an {@code ALL} hologram needs no visibility call; a {@code PERMISSION} hologram is shown when the
 * joiner holds its node, a {@code MANUAL} hologram when the joiner is in its shown-viewer set, and a hologram
 * whose lines embed a placeholder also sends the joiner their own per-viewer text override. The renderer routes
 * each show/hide and each override onto the entity's region thread. A player quitting or changing world is
 * already handled by the uxmLib hologram lifecycle listener, which forgets their viewer entry, so this listener
 * only needs the join hook.
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
