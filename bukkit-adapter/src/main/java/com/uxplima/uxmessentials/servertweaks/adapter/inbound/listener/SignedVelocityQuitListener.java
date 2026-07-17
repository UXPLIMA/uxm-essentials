package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.servertweaks.application.SignedDirectiveQueue;
import org.jspecify.annotations.NullMarked;

/**
 * Housekeeping for the SignedVelocity backend: when a player disconnects, drop any chat/command rulings still buffered
 * for them in the shared {@link SignedDirectiveQueue}. A ruling only ever pairs with the player's next event, so one
 * left unclaimed at quit (a rare race) would otherwise linger until it happened to be reused; forgetting it keeps the
 * queue from accumulating stale per-player state.
 */
@NullMarked
public final class SignedVelocityQuitListener implements Listener {

    private final SignedDirectiveQueue queue;

    public SignedVelocityQuitListener(SignedDirectiveQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    /** Forget a disconnecting player's buffered rulings. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        queue.forget(event.getPlayer().getUniqueId());
    }
}
