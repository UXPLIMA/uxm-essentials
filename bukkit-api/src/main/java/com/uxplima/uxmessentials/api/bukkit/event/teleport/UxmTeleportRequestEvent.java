package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What every teleport-request notification has in common: the request's id and the two players either side of it.
 *
 * <p>The requester is the event's subject, so {@code getPlayerId()} is theirs; the target has accessors of its own.
 * The id is stable for the life of the request, which is what lets a listener match an accept or an expiry back to
 * the request it answers.
 */
@NullMarked
public abstract class UxmTeleportRequestEvent extends UxmTeleportEvent {

    private final UUID requestId;
    private final UUID targetId;
    private final String targetName;

    protected UxmTeleportRequestEvent(
            UUID requestId, UUID requesterId, String requesterName, UUID targetId, String targetName) {
        super(requesterId, requesterName);
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.targetName = Objects.requireNonNull(targetName, "targetName");
    }

    /** The request's id, stable from the moment it is made until it is answered or expires. */
    public UUID getRequestId() {
        return requestId;
    }

    /** The id of the player who made the request. The same as {@link #getPlayerId()}. */
    public UUID getRequesterId() {
        return getPlayerId();
    }

    /** The name of the player who made the request. */
    public String getRequesterName() {
        return getPlayerName();
    }

    /** The id of the player it was made to. */
    public UUID getTargetId() {
        return targetId;
    }

    /** The name of the player it was made to. */
    public String getTargetName() {
        return targetName;
    }

    /** The player it was made to, or {@code null} when they are offline. */
    public @Nullable Player getTarget() {
        return Bukkit.getPlayer(targetId);
    }
}
