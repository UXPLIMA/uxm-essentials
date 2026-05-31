package com.uxplima.uxmessentials.presence.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.application.port.VisibilityApplier;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The presence lifecycle listener: it seeds and tears down a player's {@link PlayerPresence} and keeps the
 * vanish view coherent across join and quit (docs/10-feature-modules.md §15.8 — fake join/quit suppression).
 *
 * <ul>
 *   <li><b>Join</b> — seed the player's active presence, reconcile vanish visibility through the
 *       {@link VisibilityApplier} (re-hide a still-vanished relogged staff member), and re-hide every
 *       currently-vanished other player from the joiner so they do not flash into view. When the joining player
 *       is vanished, the fake-join line is suppressed so no one sees them connect.
 *   <li><b>Quit</b> — suppress the fake-quit line for a vanished player (they already appeared offline), then
 *       drop the presence so a disconnected player holds no state. Vanish itself is session-scoped here; the
 *       persisted-across-relog edge (the {@code vanish.persist} node) is deferred.
 * </ul>
 *
 * <p>The events fire on the player's region thread; every visibility mutation still routes through the
 * {@link VisibilityApplier}, which hops to the owning entity thread via the {@code Scheduler} port — valid on
 * Folia.
 */
@NullMarked
public final class PresenceLifecycleListener implements Listener {

    private final PresenceStore store;
    private final VisibilityApplier visibility;

    public PresenceLifecycleListener(PresenceStore store, VisibilityApplier visibility) {
        this.store = Objects.requireNonNull(store, "store");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        PlayerPresence presence = store.current(who);
        visibility.reconcileOnJoin(who, presence.vanished());
        rehideVanishedOthers(who);
        if (presence.vanished()) {
            event.joinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        if (store.current(who).vanished()) {
            event.quitMessage(null);
        }
        store.forget(who);
    }

    private void rehideVanishedOthers(PlayerRef joiner) {
        store.snapshotAll().forEach((other, presence) -> {
            if (presence.vanished() && !other.equals(joiner)) {
                visibility.hide(other);
            }
        });
    }
}
