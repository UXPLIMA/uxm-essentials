package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * One NPC as the plugin stores it: a server-unique name, where it stands, and the handful of properties a consumer
 * outside the module has a reason to read.
 *
 * <p>Not the whole aggregate. An NPC carries several dozen render details (equipment per slot, pose, scale,
 * per-entity-type metadata, per-NPC view distances) that only the renderer interprets, and publishing them would
 * pin every one of them as a compatibility promise for the sake of a consumer that wants to know where the shop
 * NPC is. What is here is what an outside plugin asks: which NPCs exist, where, who owns them, and what a click
 * does.
 *
 * @param name the NPC's id, which is what {@code /npc} commands take and what the API takes here
 * @param location where it stands and which way it faces
 * @param entityType the uppercase entity type it renders as, {@code PLAYER} for the default fake player
 * @param displayName the label shown above it, or empty when no label is set
 * @param nameHidden whether the label was explicitly cleared, so nothing at all is shown above it; with
 *     {@code displayName} empty and this false, the NPC shows its id, which is the default
 * @param clickCommand the command a click runs, or empty when a click runs none
 * @param actions how many typed click actions are bound to it, which run after {@code clickCommand}
 * @param lookAtPlayer whether it turns to face nearby players
 * @param glowing whether it is drawn with an outline
 * @param skinned whether a skin is set (the texture itself is never published: it is a render detail, and a large one)
 * @param ownerId the player who created it, or empty for one the console created
 * @param createdAt when it was created
 */
@NullMarked
public record UxmNpc(
        String name,
        UxmLocation location,
        String entityType,
        Optional<String> displayName,
        boolean nameHidden,
        Optional<String> clickCommand,
        int actions,
        boolean lookAtPlayer,
        boolean glowing,
        boolean skinned,
        Optional<UUID> ownerId,
        Instant createdAt) {

    public UxmNpc {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(clickCommand, "clickCommand");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Whether this NPC is the default fake player rather than a mob or a display entity. */
    public boolean isPlayer() {
        return "PLAYER".equals(entityType);
    }
}
