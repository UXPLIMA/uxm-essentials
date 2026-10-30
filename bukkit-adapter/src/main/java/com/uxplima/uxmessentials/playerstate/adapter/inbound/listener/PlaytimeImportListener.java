package com.uxplima.uxmessentials.playerstate.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.playerstate.adapter.PlaytimeAdmin;
import org.jspecify.annotations.NullMarked;

/**
 * {@code playtime.import-on-join}: bring a joining player's vanilla playtime into the tracked total the first time
 * they join with the setting on. A player imported before, by this or by {@code /playtime import}, is skipped.
 */
@NullMarked
public final class PlaytimeImportListener implements Listener {

    private final PlaytimeAdmin admin;

    public PlaytimeImportListener(PlaytimeAdmin admin) {
        this.admin = Objects.requireNonNull(admin, "admin");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        admin.importOnJoin(event.getPlayer());
    }
}
