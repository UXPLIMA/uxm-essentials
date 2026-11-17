package com.uxplima.uxmessentials.shared.adapter.inbound.lookup;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.OnlinePlayerIndex;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the {@link OnlinePlayerIndex} in step with the server. A player is indexed as soon as their login is allowed,
 * at the end of the login event, so every join handler, the lowest of any plugin included, already finds them by
 * name, as it would asking the server. A quit is recorded last. A login that never becomes a join (the connection
 * closed during configuration) is taken out again when its connection closes. The live {@code Player} names the
 * connection, a new object for every login, so a late quit of an old one is told apart.
 */
// PlayerLoginEvent is the last event before the join where the Player exists and its login outcome is final, so the
// API's deprecation hint toward the newer pipeline is not taken, as in the moderation login check.
@SuppressWarnings("deprecation")
@NullMarked
public final class OnlinePlayerIndexListener implements Listener {

    private final OnlinePlayerIndex index;
    /** Logins allowed whose join has not happened yet, by account. */
    private final Map<UUID, Player> joining = new ConcurrentHashMap<>();

    public OnlinePlayerIndexListener(OnlinePlayerIndex index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        Player player = event.getPlayer();
        joining.put(player.getUniqueId(), player);
        index.joined(BukkitRefs.toRef(player), player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joining.remove(player.getUniqueId(), player);
        // Indexed again in case the login was allowed after this listener saw it, by a plugin listening later.
        index.joined(BukkitRefs.toRef(player), player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        joining.remove(event.getPlayer().getUniqueId(), event.getPlayer());
        index.left(event.getPlayer().getUniqueId(), event.getPlayer());
    }

    /**
     * A connection closed: a login that never joined leaves no player behind in the index. The entry is taken only
     * when its own connection is the one gone, so the late close of an earlier connection leaves a newer login alone.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(PlayerConnectionCloseEvent event) {
        UUID account = event.getPlayerUniqueId();
        Player pending = joining.get(account);
        if (pending != null && !pending.isConnected() && joining.remove(account, pending)) {
            index.left(account, pending);
        }
    }
}
