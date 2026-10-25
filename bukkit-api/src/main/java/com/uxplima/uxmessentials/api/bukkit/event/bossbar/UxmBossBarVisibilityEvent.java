package com.uxplima.uxmessentials.api.bukkit.event.bossbar;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/** A player hid or showed their boss bars. */
@NullMarked
public final class UxmBossBarVisibilityEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean hidden;

    public UxmBossBarVisibilityEvent(UUID playerId, String playerName, boolean hidden) {
        super(playerId, playerName);
        this.hidden = hidden;
    }

    /** Whether the bars are hidden now. */
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
