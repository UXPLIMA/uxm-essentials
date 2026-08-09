package com.uxplima.uxmessentials.api.bukkit.event.scoreboard;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/** A player showed or hid their sidebar. */
@NullMarked
public final class UxmScoreboardVisibilityEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean hidden;

    public UxmScoreboardVisibilityEvent(UUID playerId, String playerName, boolean hidden) {
        super(playerId, playerName);
        this.hidden = hidden;
    }

    /** Whether the sidebar is now hidden. */
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
