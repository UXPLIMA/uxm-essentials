package com.uxplima.uxmessentials.playerstate.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The playerstate context's lifecycle listener: it keeps the live player's god/fly/speed flags in step with
 * their in-memory {@link PlayerStateSnapshot} across the events that reset Bukkit state.
 *
 * <ul>
 *   <li><b>Join</b> — re-apply the snapshot so a returning player keeps the flags they held this session
 *       (the in-memory map survives a relog within the same server uptime; it is transient, not persisted).
 *   <li><b>Respawn</b> — re-apply, because a death resets {@code allowFlight} and invulnerability; the
 *       reconciler runs after the respawn so the values stick.
 *   <li><b>Quit</b> — drop the snapshot so a disconnected player holds no state.
 * </ul>
 *
 * <p>The events fire on the player's region thread, but reconciliation is still routed through the
 * {@link StateReconciler}, which hops to the owning entity thread via the {@code Scheduler} port — the one
 * place Bukkit state is mutated, valid on Folia.
 */
@NullMarked
public final class PlayerStateListener implements Listener {

    private final PlayerStateStore store;
    private final StateReconciler reconciler;

    public PlayerStateListener(PlayerStateStore store, StateReconciler reconciler) {
        this.store = Objects.requireNonNull(store, "store");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        reapply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        reapply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        store.forget(BukkitRefs.toRef(event.getPlayer()));
    }

    private void reapply(Player player) {
        PlayerRef ref = BukkitRefs.toRef(player);
        reconciler.reconcile(ref, store.current(ref));
    }
}
