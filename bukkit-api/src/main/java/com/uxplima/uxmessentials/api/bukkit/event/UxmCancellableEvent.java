package com.uxplima.uxmessentials.api.bukkit.event;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import org.jspecify.annotations.NullMarked;

/**
 * The base of every uxmEssentials pre-event: an action that has not happened yet, and that you may cancel.
 *
 * <h2>Treat these as asynchronous</h2>
 * uxmEssentials runs its use cases off the tick thread, because they hit the database. The veto question therefore
 * reaches you on that thread, and two rules follow. Breaking either breaks the server rather than us:
 *
 * <ul>
 *   <li>Do not touch the Bukkit API from the handler. Read the event, decide, return. Schedule anything else.
 *   <li>Keep the handler cheap. The operation you are being asked about is blocked on your answer, and a player is
 *       waiting for it.
 * </ul>
 *
 * <h2>What cancelling does</h2>
 * The operation fails cleanly: nothing is written, and the player is told the action was blocked. It is not an
 * error, so nothing is logged as one; if you want the player to know <em>why</em>, send them a message yourself
 * (scheduled onto their region, since you are off the tick thread here).
 *
 * <p>Only actions worth vetoing have a pre-event. Everything uxmEssentials does also publishes a notification
 * {@link UxmEvent} after the fact, which is what to listen to when you want to observe rather than block.
 *
 * <p>Most vetoable actions are one player's and extend {@link UxmPlayerCancellableEvent}, which names them.
 */
@NullMarked
public abstract class UxmCancellableEvent extends Event implements Cancellable {

    private boolean cancelled;

    protected UxmCancellableEvent() {
        // Whichever thread the use case is on is the thread you get, because the answer is needed before it can go
        // any further. In practice that is an async one; the flag simply tells the truth about it rather than
        // asserting a thread the caller never promised.
        super(!Bukkit.isPrimaryThread());
    }

    @Override
    public final boolean isCancelled() {
        return cancelled;
    }

    @Override
    public final void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
