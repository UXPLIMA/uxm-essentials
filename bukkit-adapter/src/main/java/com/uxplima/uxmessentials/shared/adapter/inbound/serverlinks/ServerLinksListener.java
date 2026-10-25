package com.uxplima.uxmessentials.shared.adapter.inbound.serverlinks;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import org.jspecify.annotations.NullMarked;

/**
 * Sends a player their own copy of the pause-menu links. The copy every client receives while it connects reads in
 * the server's language, because the server does not know the player's choice before they join; a client that
 * changes language reads the new one at once.
 */
@NullMarked
public final class ServerLinksListener implements Listener {

    private final Consumer<Player> send;
    private final Consumer<UUID> forget;

    public ServerLinksListener(Consumer<Player> send, Consumer<UUID> forget) {
        this.send = Objects.requireNonNull(send, "send");
        this.forget = Objects.requireNonNull(forget, "forget");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        send.accept(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        send.accept(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget.accept(event.getPlayer().getUniqueId());
    }
}
