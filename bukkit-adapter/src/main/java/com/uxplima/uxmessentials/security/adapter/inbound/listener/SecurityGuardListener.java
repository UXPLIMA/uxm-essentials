package com.uxplima.uxmessentials.security.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.security.adapter.ClientBrandRegistry;
import com.uxplima.uxmessentials.security.adapter.ClientGuard;
import com.uxplima.uxmessentials.security.adapter.IpGuardController;
import org.jspecify.annotations.NullMarked;

/**
 * The connection edges of the Phase-4 guards: on join it hands the player to the {@link IpGuardController} (record
 * the hashed IP, enforce the same-IP cap, notify staff of alts) and the {@link ClientGuard} (read and judge the
 * client brand); on quit it drops the player's recorded brand so the session-only registry never grows. Both edges
 * fire at {@code MONITOR} — the guards only read state and schedule their own off-thread work, so they run after
 * every other join handler has settled the player in. Each guard self-gates on its own config flag, so a disabled
 * sub-feature is a no-op even though the listener is registered.
 */
@NullMarked
public final class SecurityGuardListener implements Listener {

    private final IpGuardController ipGuard;
    private final ClientGuard clientGuard;
    private final ClientBrandRegistry brands;

    public SecurityGuardListener(IpGuardController ipGuard, ClientGuard clientGuard, ClientBrandRegistry brands) {
        this.ipGuard = Objects.requireNonNull(ipGuard, "ipGuard");
        this.clientGuard = Objects.requireNonNull(clientGuard, "clientGuard");
        this.brands = Objects.requireNonNull(brands, "brands");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        ipGuard.onJoin(event.getPlayer());
        clientGuard.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        brands.clear(event.getPlayer().getUniqueId());
    }
}
