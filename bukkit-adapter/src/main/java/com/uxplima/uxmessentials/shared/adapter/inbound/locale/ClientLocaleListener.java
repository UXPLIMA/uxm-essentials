package com.uxplima.uxmessentials.shared.adapter.inbound.locale;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.message.ClientLocales;

/**
 * Keeps {@link ClientLocales} current: the language a player's client reads, from join to quit.
 *
 * <p>Each event arrives on the thread that owns the player, which is the one thread allowed to ask. The join is read
 * at the lowest priority so a welcome another listener writes on join already finds the language.
 */
public final class ClientLocaleListener implements Listener {

    private final ClientLocales clients;

    public ClientLocaleListener(ClientLocales clients) {
        this.clients = Objects.requireNonNull(clients, "clients");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        clients.remember(event.getPlayer().getUniqueId(), event.getPlayer().locale());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitch(PlayerLocaleChangeEvent event) {
        clients.remember(event.getPlayer().getUniqueId(), event.locale());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clients.forget(event.getPlayer().getUniqueId());
    }
}
