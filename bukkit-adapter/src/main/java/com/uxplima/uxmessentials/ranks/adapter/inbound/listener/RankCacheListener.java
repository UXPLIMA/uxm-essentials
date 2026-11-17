package com.uxplima.uxmessentials.ranks.adapter.inbound.listener;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.uxplima.uxmessentials.ranks.application.CachedPlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.RankActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the {@link CachedPlayerRankRepository} on the online set. The pointer is read during the async pre-login and
 * waited on for a short bounded time, so a join message or the first scoreboard frame usually shows the real rank
 * without a slow database ever holding a login. The join loads again on the async lane only when the pre-login read
 * did not land. A connection that passed the pre-login but closes without joining (a configuration-phase drop, a
 * refused resource pack, a timeout, a refused login) gets no quit event, so the connection close forgets it too.
 *
 * <p>A join also runs the rank actions kept for the player while they were not here, on whichever server they join.
 */
@NullMarked
public final class RankCacheListener implements Listener {

    /** Long enough for a healthy database, short enough that nobody notices it in a login. */
    private static final Duration PRELOGIN_WAIT = Duration.ofMillis(1500);

    private final CachedPlayerRankRepository cache;
    private final RankActions actions;
    private final PlayerLookup players;

    public RankCacheListener(CachedPlayerRankRepository cache, RankActions actions, PlayerLookup players) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.players = Objects.requireNonNull(players, "players");
    }

    /**
     * Load every player already online, for a module enabled or reloaded while they play, and run what was kept for
     * them while the module was off.
     */
    public void loadOnline(Collection<? extends Player> players) {
        for (Player player : players) {
            cache.join(player.getUniqueId());
            actions.joined(BukkitRefs.toRef(player));
        }
    }

    // MONITOR so a login another plugin refuses is never loaded.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            cache.preload(event.getUniqueId(), PRELOGIN_WAIT);
        }
    }

    // LOWEST so a pointer the pre-login missed is already queued when later join handlers ask for it.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        cache.join(event.getPlayer().getUniqueId());
    }

    // MONITOR so the kept actions run once every other plugin, a permission plugin above all, has the player loaded.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoined(PlayerJoinEvent event) {
        actions.joined(BukkitRefs.toRef(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.quit(event.getPlayer().getUniqueId());
    }

    /**
     * Fires for every connection that passed the pre-login, after the quit when the player had joined. A duplicate
     * login closes the old connection while the new session for the same player is already online, and that session
     * must keep its pointer, so only a player who is not online now is forgotten. The close can fire off the tick, so
     * the online index answers that rather than the server's player list.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        UUID playerId = event.getPlayerUniqueId();
        if (!players.isOnline(playerId)) {
            cache.quit(playerId);
        }
    }
}
