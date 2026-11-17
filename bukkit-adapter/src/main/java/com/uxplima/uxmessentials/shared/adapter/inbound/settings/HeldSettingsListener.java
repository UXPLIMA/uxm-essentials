package com.uxplima.uxmessentials.shared.adapter.inbound.settings;

import java.util.Collection;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;
import org.jspecify.annotations.NullMarked;

/**
 * Reads a player's shared settings during the asynchronous pre-login, so they are in memory before any command or
 * message of theirs is handled, and lets go of them when the connection closes.
 */
@NullMarked
public final class HeldSettingsListener implements Listener {

    private final HeldSettings settings;

    public HeldSettingsListener(HeldSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Monitor, so a login another plugin refuses reads nothing; the event already runs off the tick. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            settings.load(event.getUniqueId());
        }
    }

    /** A quit, or a login that was read but never reached the world. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        settings.forget(event.getPlayerUniqueId());
    }

    /** Read the settings of the players already online when the module started, off the tick. */
    public void adoptOnline(Collection<? extends Player> online, Scheduler scheduler) {
        for (Player player : online) {
            java.util.UUID id = player.getUniqueId();
            scheduler.async(() -> settings.load(id));
        }
    }
}
