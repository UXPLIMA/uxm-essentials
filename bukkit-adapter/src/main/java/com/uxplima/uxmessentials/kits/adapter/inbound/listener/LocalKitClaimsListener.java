package com.uxplima.uxmessentials.kits.adapter.inbound.listener;

import java.util.Collection;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.uxplima.uxmessentials.kits.application.LocalKitClaims;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;

/**
 * Brings the one-time kit claims the database records for a player into their stamps on a single server: read during
 * the asynchronous pre-login, stamped as they join, before any command or menu of theirs can claim a kit.
 */
@NullMarked
public final class LocalKitClaimsListener implements Listener {

    private final LocalKitClaims claims;

    public LocalKitClaimsListener(LocalKitClaims claims) {
        this.claims = Objects.requireNonNull(claims, "claims");
    }

    /** Monitor, so a login another plugin refuses reads nothing; the event already runs off the tick. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            claims.load(event.getUniqueId());
        }
    }

    /** Lowest, so the stamps are in place before another join listener can act on them. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        claims.joined(BukkitRefs.toRef(event.getPlayer()));
    }

    /** A login that was read but never reached the world lets go of what was read for it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        claims.forget(event.getPlayerUniqueId());
    }

    /** Bring in the recorded claims of players already online when the module started, each on their own thread. */
    public void adoptOnline(Collection<? extends Player> online) {
        for (Player player : online) {
            claims.adopt(BukkitRefs.toRef(player));
        }
    }
}
