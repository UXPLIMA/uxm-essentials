package com.uxplima.uxmessentials.api.bukkit.event.itemworld;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A staff member spawned mobs with {@code /spawnmob}.
 *
 * <p>The requested and spawned counts differ when the configured cap cut the request short.
 */
@NullMarked
public final class UxmMobSpawnEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String worldName;
    private final String entityType;
    private final int requested;
    private final int spawned;
    private final Instant at;

    public UxmMobSpawnEvent(
            UUID actorId,
            String actorName,
            String worldName,
            String entityType,
            int requested,
            int spawned,
            Instant at) {
        super(actorId, actorName);
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.requested = requested;
        this.spawned = spawned;
        this.at = Objects.requireNonNull(at, "at");
    }

    /** The world they were spawned in. */
    public String getWorldName() {
        return worldName;
    }

    /** The entity type, as typed. */
    public String getEntityType() {
        return entityType;
    }

    /** How many were asked for. */
    public int getRequested() {
        return requested;
    }

    /** How many actually appeared. */
    public int getSpawned() {
        return spawned;
    }

    /** When it happened. */
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
