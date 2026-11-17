package com.uxplima.uxmessentials.shared.adapter.inbound.lookup;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.uxplima.uxmessentials.shared.application.FirstJoins;
import org.jspecify.annotations.NullMarked;

/**
 * Reads, during the login, whether the network knows a joining account, so a join listener can tell a player new to
 * the network from one only new to this server. On a single server nothing is read: the server's own answer is the
 * whole answer there.
 */
@NullMarked
public final class FirstJoinListener implements Listener {

    private final FirstJoins firstJoins;
    private final boolean networked;
    private final Predicate<UUID> online;

    /**
     * @param networked whether this server is part of a network ({@code network.enabled})
     * @param online whether a player is online on this server
     */
    public FirstJoinListener(FirstJoins firstJoins, boolean networked, Predicate<UUID> online) {
        this.firstJoins = Objects.requireNonNull(firstJoins, "firstJoins");
        this.networked = networked;
        this.online = Objects.requireNonNull(online, "online");
    }

    /**
     * At {@link EventPriority#MONITOR}, once the login is let in, and before the join records the account in the
     * index; the event runs off the tick, so the read costs no server thread.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (networked && event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            firstJoins.loggingIn(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        firstJoins.forget(event.getPlayer().getUniqueId());
    }

    /** A login refused after it was read; a second login of a player online here keeps the answer it read. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        if (!online.test(event.getPlayerUniqueId())) {
            firstJoins.forget(event.getPlayerUniqueId());
        }
    }
}
