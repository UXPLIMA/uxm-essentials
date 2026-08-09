package com.uxplima.uxmessentials.api.bukkit.event.npc;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What every NPC notification has in common: which NPC, and the staff member behind the change when there was one.
 *
 * <p>The actor is nullable rather than required because not every NPC change is somebody's command: an NPC that walks
 * a path moves without anyone asking it to. A create or a delete always names a player; a move usually does not.
 */
@NullMarked
public abstract class UxmNpcEvent extends UxmEvent {

    private final String npcName;
    private final @Nullable UUID actorId;
    private final @Nullable String actorName;

    protected UxmNpcEvent(String npcName, @Nullable UUID actorId, @Nullable String actorName) {
        this.npcName = Objects.requireNonNull(npcName, "npcName");
        this.actorId = actorId;
        this.actorName = actorName;
    }

    /** The NPC's name, as typed in {@code /npc}. */
    public String getNpcName() {
        return npcName;
    }

    /** The id of the player who caused this, or {@code null} when nobody did. */
    public @Nullable UUID getActorId() {
        return actorId;
    }

    /** The name of the player who caused this, or {@code null} when nobody did. */
    public @Nullable String getActorName() {
        return actorName;
    }

    /** The player who caused this, or {@code null} when nobody did or they have since logged out. */
    public @Nullable Player getActor() {
        return actorId == null ? null : Bukkit.getPlayer(actorId);
    }
}
