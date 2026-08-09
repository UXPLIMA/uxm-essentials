package com.uxplima.uxmessentials.api.bukkit.event.kit;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerCancellableEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A kit is about to be handed out. Cancel to refuse it.
 *
 * <p>Fired once the recipient is known to be allowed the kit and off cooldown, and before anything is charged or any
 * item is placed, so a refusal costs them neither money nor a cooldown.
 */
@NullMarked
public final class UxmKitPreClaimEvent extends UxmPlayerCancellableEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String kitId;
    private final UUID actorId;
    private final String actorName;

    public UxmKitPreClaimEvent(UUID recipientId, String recipientName, String kitId, UUID actorId, String actorName) {
        super(recipientId, recipientName);
        this.kitId = Objects.requireNonNull(kitId, "kitId");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.actorName = Objects.requireNonNull(actorName, "actorName");
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

    /** Whether the recipient is claiming it themselves. */
    public boolean isSelfClaimed() {
        return actorId.equals(getPlayerId());
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
