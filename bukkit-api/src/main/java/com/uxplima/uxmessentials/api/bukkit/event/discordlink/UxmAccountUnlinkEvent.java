package com.uxplima.uxmessentials.api.bukkit.event.discordlink;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A binding was removed.
 *
 * <p>Carries the Discord account that was bound, because by the time this fires there is nowhere left to look it
 * up. Fires whether the player unlinked themselves, a plugin did it, or an operator did.
 */
@NullMarked
public final class UxmAccountUnlinkEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String discordId;

    public UxmAccountUnlinkEvent(UUID playerId, String playerName, String discordId) {
        super(playerId, playerName);
        this.discordId = Objects.requireNonNull(discordId, "discordId");
    }

    /** The Discord user's id it was bound to. */
    public String getDiscordId() {
        return discordId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
