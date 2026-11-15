package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.guard.adapter.outbound.RegionDisplay;

/**
 * Takes down a player's region borders when they quit. The timed hide runs on their entity, which is gone once they
 * leave, so without this the displays drawn for them would stand until the next restart.
 */
public final class DisplayQuitListener implements Listener {

    private final RegionDisplay display;

    public DisplayQuitListener(RegionDisplay display) {
        this.display = Objects.requireNonNull(display, "display");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        display.hide(event.getPlayer());
    }
}
