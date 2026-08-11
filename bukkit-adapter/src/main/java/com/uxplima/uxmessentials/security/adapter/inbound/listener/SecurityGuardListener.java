package com.uxplima.uxmessentials.security.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.security.adapter.ClientBrandRegistry;
import com.uxplima.uxmessentials.security.adapter.ClientGuard;
import org.jspecify.annotations.NullMarked;

/**
 * The connection edges of the client guard: on join it hands the player to the {@link ClientGuard} (read and judge
 * the client brand); on quit it drops the player's recorded brand so the session-only registry never grows. Both
 * edges fire at {@code MONITOR}: the guard only reads state and schedules its own off-thread work, so it runs
 * after every other join handler has settled the player in, and it self-gates on its own config flag, so a
 * disabled sub-feature is a no-op even though the listener is registered.
 *
 * <p>The same-IP alt guard is not here. It watches the kernel IP-history recorder instead, so the association is
 * written once, by one capture, and read only after that write lands.
 */
@NullMarked
public final class SecurityGuardListener implements Listener {

    private final ClientGuard clientGuard;
    private final ClientBrandRegistry brands;

    public SecurityGuardListener(ClientGuard clientGuard, ClientBrandRegistry brands) {
        this.clientGuard = Objects.requireNonNull(clientGuard, "clientGuard");
        this.brands = Objects.requireNonNull(brands, "brands");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        clientGuard.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        brands.clear(event.getPlayer().getUniqueId());
    }
}
