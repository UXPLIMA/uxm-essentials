package com.uxplima.uxmessentials.api.bukkit.event.playerstate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What every player-state notification has in common: whose state changed, and who changed it.
 *
 * <p>The subject and the actor are the same person when a player ran the command on themselves, which is the ordinary
 * case; they differ when a staff member did it for them. Comparing the two is how a listener tells {@code /heal} from
 * {@code /heal <player>}.
 */
@NullMarked
public abstract class UxmPlayerStateEvent extends UxmPlayerEvent {

    private final UUID actorId;
    private final String actorName;
    private final Instant at;

    protected UxmPlayerStateEvent(UUID subjectId, String subjectName, UUID actorId, String actorName, Instant at) {
        super(subjectId, subjectName);
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.actorName = Objects.requireNonNull(actorName, "actorName");
        this.at = Objects.requireNonNull(at, "at");
    }

    /** The id of whoever made the change. */
    public UUID getActorId() {
        return actorId;
    }

    /** The name of whoever made the change. */
    public String getActorName() {
        return actorName;
    }

    /** Whoever made the change, or {@code null} when they are offline. */
    public @Nullable Player getActor() {
        return Bukkit.getPlayer(actorId);
    }

    /** Whether the player did this to themselves. */
    public boolean isSelfInflicted() {
        return actorId.equals(getPlayerId());
    }

    /** When it happened. */
    public Instant getAt() {
        return at;
    }
}
