package com.uxplima.uxmessentials.api.bukkit.event.discordlink;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player's Minecraft account and a Discord account are now bound.
 *
 * <p>The code is redeemed on Discord's side, so this fires for a player who is very often offline. Do not assume
 * a live player from it.
 */
@NullMarked
public final class UxmAccountLinkEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String discordId;
    private final Instant linkedAt;

    public UxmAccountLinkEvent(UUID playerId, String playerName, String discordId, Instant linkedAt) {
        super(playerId, playerName);
        this.discordId = Objects.requireNonNull(discordId, "discordId");
        this.linkedAt = Objects.requireNonNull(linkedAt, "linkedAt");
    }

    /** The Discord user's id, as a string, the way Discord's own API writes a snowflake. */
    public String getDiscordId() {
        return discordId;
    }

    /** When the binding was confirmed. */
    public Instant getLinkedAt() {
        return linkedAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
