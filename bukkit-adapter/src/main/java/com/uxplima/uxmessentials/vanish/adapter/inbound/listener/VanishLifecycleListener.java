package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the vanish view coherent across join and quit.
 *
 * <ul>
 *   <li><b>Join</b> — re-hide every currently-vanished other player from the joiner, so a hidden staff member does not
 *       flash into view the moment someone connects. The joiner is never itself vanished on a fresh join (the state is
 *       transient and was dropped on their last quit), so there is nothing to re-hide for them.
 *   <li><b>Quit</b> — suppress the quit line for a vanished player (they already appeared offline to those who could
 *       not see them), then drop them from the vanish store so a disconnected player holds no vanish state and a later
 *       reconnect starts visible. The configurable fake join/quit broadcast is a later phase.
 * </ul>
 *
 * <p>The events fire on the player's region thread; every hide still routes through the {@link VanishView}, which hops
 * to the owning entity thread via the {@code Scheduler} port — valid on Folia.
 */
@NullMarked
public final class VanishLifecycleListener implements Listener {

    private final VanishStore store;
    private final VanishView view;
    private final Server server;

    public VanishLifecycleListener(VanishStore store, VanishView view, Server server) {
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.server = Objects.requireNonNull(server, "server");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID joiner = event.getPlayer().getUniqueId();
        for (UUID vanished : store.vanished()) {
            if (vanished.equals(joiner)) {
                continue;
            }
            @Nullable Player other = server.getPlayer(vanished);
            if (other != null && other.isOnline()) {
                view.hide(BukkitRefs.toRef(other));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID who = event.getPlayer().getUniqueId();
        if (store.isVanished(who)) {
            event.quitMessage(null);
        }
        store.reveal(who);
    }
}
