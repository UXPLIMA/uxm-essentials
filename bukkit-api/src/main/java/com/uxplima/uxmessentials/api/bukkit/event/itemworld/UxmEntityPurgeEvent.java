package com.uxplima.uxmessentials.api.bukkit.event.itemworld;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import com.uxplima.uxmessentials.api.view.UxmPurgeCategory;
import com.uxplima.uxmessentials.api.view.UxmPurgeScope;
import org.jspecify.annotations.NullMarked;

/** Entities were cleaned up by {@code /butcher}, {@code /killall} or {@code /remove}. Players are never removed. */
@NullMarked
public final class UxmEntityPurgeEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String worldName;
    private final UxmPurgeScope scope;
    private final UxmPurgeCategory category;
    private final int radius;
    private final Optional<String> entityType;
    private final int removed;
    private final Instant at;

    public UxmEntityPurgeEvent(
            UUID actorId,
            String actorName,
            String worldName,
            UxmPurgeScope scope,
            UxmPurgeCategory category,
            int radius,
            Optional<String> entityType,
            int removed,
            Instant at) {
        super(actorId, actorName);
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.category = Objects.requireNonNull(category, "category");
        this.radius = radius;
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.removed = removed;
        this.at = Objects.requireNonNull(at, "at");
    }

    /** The world it swept. */
    public String getWorldName() {
        return worldName;
    }

    /** How wide it swept. */
    public UxmPurgeScope getScope() {
        return scope;
    }

    /** What class of entity it removed. */
    public UxmPurgeCategory getCategory() {
        return category;
    }

    /** The radius swept, meaningful only when the scope is {@link UxmPurgeScope#RADIUS}. */
    public int getRadius() {
        return radius;
    }

    /** The named type, present only when the category is {@link UxmPurgeCategory#NAMED_TYPE}. */
    public Optional<String> getEntityType() {
        return entityType;
    }

    /** How many entities were removed. */
    public int getRemoved() {
        return removed;
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
