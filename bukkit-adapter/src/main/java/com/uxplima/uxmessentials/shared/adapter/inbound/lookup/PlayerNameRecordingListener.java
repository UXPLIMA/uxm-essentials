package com.uxplima.uxmessentials.shared.adapter.inbound.lookup;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the {@link PlayerNameIndex} current: every join records the name the account joined under, so a later
 * command can resolve that account by name in any case, on an online-mode and an offline-mode server alike.
 *
 * <p>The index write is memory-first and persists off-thread, so this handler adds no tick-thread I/O.
 */
@NullMarked
public final class PlayerNameRecordingListener implements Listener {

    private final PlayerNameIndex index;

    public PlayerNameRecordingListener(PlayerNameIndex index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        index.record(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
