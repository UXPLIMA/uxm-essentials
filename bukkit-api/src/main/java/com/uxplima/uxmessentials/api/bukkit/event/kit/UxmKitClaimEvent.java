package com.uxplima.uxmessentials.api.bukkit.event.kit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A kit was claimed. The items are already in the recipient's inventory and the cooldown is already stamped.
 *
 * <p>The recipient is the subject; the actor differs from them when a staff member gave the kit out.
 */
@NullMarked
public final class UxmKitClaimEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String kitId;
    private final UUID actorId;
    private final String actorName;
    private final Instant at;

    public UxmKitClaimEvent(
            UUID recipientId, String recipientName, String kitId, UUID actorId, String actorName, Instant at) {
        super(recipientId, recipientName);
        this.kitId = Objects.requireNonNull(kitId, "kitId");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.actorName = Objects.requireNonNull(actorName, "actorName");
        this.at = Objects.requireNonNull(at, "at");
    }

    /** Which kit, by its configured id. */
    public String getKitId() {
        return kitId;
    }

    /** The id of whoever ran the command. */
    public UUID getActorId() {
        return actorId;
    }

    /** The name of whoever ran the command. */
    public String getActorName() {
        return actorName;
    }

    /** Whether the recipient claimed it themselves. */
    public boolean isSelfClaimed() {
        return actorId.equals(getPlayerId());
    }

    /** When it was claimed. */
    public Instant getAt() {
        return at;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
