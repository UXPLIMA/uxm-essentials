package com.uxplima.uxmessentials.api.bukkit.event;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import org.jspecify.annotations.NullMarked;

/**
 * The base of every uxmEssentials pre-event: an action that has not happened yet, and that you may cancel.
 *
 * <h2>These are asynchronous, and that is deliberate</h2>
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
 */
@NullMarked
public abstract class UxmCancellableEvent extends Event implements Cancellable {

    private final UUID subjectId;
    private final String subjectName;
    private boolean cancelled;

    protected UxmCancellableEvent(UUID subjectId, String subjectName) {
        super(true); // asynchronous: fired from the use case's own thread, off the tick
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.subjectName = Objects.requireNonNull(subjectName, "subjectName");
    }

    /** The id of the player whose action this is. Always present, online or not. */
    public UUID getPlayerId() {
        return subjectId;
    }

    /** The name of the player whose action this is, as uxmEssentials last knew it. */
    public String getPlayerName() {
        return subjectName;
    }

    /**
     * The player whose action this is, as an offline handle. There is deliberately no live {@code Player} accessor:
     * this event is fired off the tick thread, where acting on a live player is unsafe.
     */
    public OfflinePlayer getOfflinePlayer() {
        return Bukkit.getOfflinePlayer(subjectId);
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
