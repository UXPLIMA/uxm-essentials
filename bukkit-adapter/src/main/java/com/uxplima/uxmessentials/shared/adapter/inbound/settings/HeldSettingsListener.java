package com.uxplima.uxmessentials.shared.adapter.inbound.settings;

import java.util.Collection;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.settings.SettingsSessions;
import org.jspecify.annotations.NullMarked;

/**
 * Hands the moments of a player's connection to {@link SettingsSessions}: their shared settings are read during the
 * asynchronous pre-login, read again at the join when that read did not leave them held, and let go of at the quit or
 * when a login that never joined closes.
 */
@NullMarked
public final class HeldSettingsListener implements Listener {

    private final SettingsSessions sessions;
    private final PlayerLookup players;

    public HeldSettingsListener(SettingsSessions sessions, PlayerLookup players) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.players = Objects.requireNonNull(players, "players");
    }

    /** Monitor, so a login another plugin refuses reads nothing; the event already runs off the tick. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            sessions.loggingIn(event.getUniqueId());
        }
    }

    /** Lowest, so a read the pre-login missed is already queued when later join handlers act. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        sessions.joined(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.quit(event.getPlayer().getUniqueId());
    }

    /**
     * A duplicate login closes the old connection while the new session is already online; the close can fire off the
     * tick, so the online index answers that rather than the server's player list.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        sessions.closed(event.getPlayerUniqueId(), players.isOnline(event.getPlayerUniqueId()));
    }

    /** Read the settings of the players already online when the module started, off the tick. */
    public void adoptOnline(Collection<? extends Player> online) {
        for (Player player : online) {
            sessions.adopt(player.getUniqueId());
        }
    }
}
