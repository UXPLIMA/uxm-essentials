package com.uxplima.uxmessentials.npc.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;

/**
 * One server-wide fake-player NPC: a {@link NpcName}, the {@link Position} it stands at, an optional
 * {@link NpcSkin}, the optional command run when a player clicks it, and the moment it was created. An NPC is a
 * value object — moving it, re-skinning it, or rebinding its click command produces a new instance rather than
 * mutating in place, so the aggregate is always in a valid state and a repository save records a fully-formed
 * snapshot.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the NPC's world
 * is read from {@code location().world()} rather than held separately. A {@code null} skin renders the default
 * (Steve) fake player; a {@code null} click command means clicking the NPC does nothing. The click command is
 * stored as raw text here — running it on interaction is an adapter concern, so the domain only carries the
 * binding. {@code lookAtPlayer} controls whether the fake player rotates to face each nearby viewer; it defaults
 * to {@code true} so a freshly created NPC tracks players out of the box.
 *
 * @param name the NPC's canonical, server-unique name
 * @param location where the NPC stands and which way it faces
 * @param skin the fake player's skin, or {@code null} for the default skin
 * @param clickCommand the command run when a player clicks the NPC, or {@code null} for no action
 * @param lookAtPlayer whether the NPC rotates to face each nearby viewer
 * @param createdAt when the NPC was first created (preserved across a move, re-skin, or rebind)
 */
public record Npc(
        NpcName name,
        Position location,
        @Nullable NpcSkin skin,
        @Nullable String clickCommand,
        boolean lookAtPlayer,
        Instant createdAt) {

    public Npc {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A new NPC created now at {@code location} with the given (possibly {@code null}) skin, no command, looking. */
    public static Npc create(NpcName name, Position location, @Nullable NpcSkin skin, Instant createdAt) {
        return new Npc(name, location, skin, null, true, createdAt);
    }

    /** A copy re-anchored to {@code newLocation}, keeping everything else. */
    public Npc movedTo(Position newLocation) {
        Objects.requireNonNull(newLocation, "newLocation");
        return new Npc(name, newLocation, skin, clickCommand, lookAtPlayer, createdAt);
    }

    /** A copy wearing {@code newSkin} (or {@code null} to reset to the default skin), keeping everything else. */
    public Npc withSkin(@Nullable NpcSkin newSkin) {
        return new Npc(name, location, newSkin, clickCommand, lookAtPlayer, createdAt);
    }

    /** A copy whose click runs {@code newCommand} (or {@code null} to clear it), keeping everything else. */
    public Npc withClickCommand(@Nullable String newCommand) {
        return new Npc(name, location, skin, newCommand, lookAtPlayer, createdAt);
    }

    /** A copy that does or does not rotate to face nearby viewers, keeping everything else. */
    public Npc withLookAtPlayer(boolean newLookAtPlayer) {
        return new Npc(name, location, skin, clickCommand, newLookAtPlayer, createdAt);
    }

    /** Whether this NPC carries a skin (a fake player with no skin renders the default Steve). */
    public boolean hasSkin() {
        return skin != null;
    }

    /** Whether clicking this NPC runs a command. */
    public boolean hasClickCommand() {
        return clickCommand != null && !clickCommand.isBlank();
    }
}
