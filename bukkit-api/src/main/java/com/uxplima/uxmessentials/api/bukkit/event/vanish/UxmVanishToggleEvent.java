package com.uxplima.uxmessentials.api.bukkit.event.vanish;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player went hidden, or came back into view.
 *
 * <p>Fires for every door into vanish: the command, the staff-mode toggle, the presence panel, and a plugin calling
 * the published action. By the time it fires the player is already hidden or already visible, so a listener that
 * mirrors the state elsewhere can read it straight off this event rather than asking again.
 *
 * <p>A quit does not fire this. A player who logs out hidden stays hidden, and treating the disconnect as a reveal
 * would make every listener flicker on a server hop.
 */
@NullMarked
public final class UxmVanishToggleEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean vanished;
    private final int level;

    public UxmVanishToggleEvent(UUID playerId, String playerName, boolean vanished, int level) {
        super(playerId, playerName);
        this.vanished = vanished;
        this.level = level;
    }

    /** True when the player is now hidden, false when they are visible again. */
    public boolean isVanished() {
        return vanished;
    }

    /**
     * The tier they are hidden at, counting from one. On a reveal this is the tier they were hidden at until a
     * moment ago, so a listener can undo whatever it did when they vanished.
     */
    public int getLevel() {
        return level;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
