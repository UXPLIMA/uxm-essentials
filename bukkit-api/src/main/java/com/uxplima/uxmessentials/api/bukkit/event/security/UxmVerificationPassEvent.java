package com.uxplima.uxmessentials.api.bukkit.event.security;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player proved their second factor and is through the join freeze.
 *
 * <p>Only fires for a proof that was made. A player who holds no factor is never asked, and one waved through by a
 * remembered device proved nothing this time.
 */
@NullMarked
public final class UxmVerificationPassEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmVerificationPassEvent(UUID playerId, String playerName) {
        super(playerId, playerName);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
