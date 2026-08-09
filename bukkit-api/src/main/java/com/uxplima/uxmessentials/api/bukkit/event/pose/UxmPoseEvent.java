package com.uxplima.uxmessentials.api.bukkit.event.pose;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmPoseType;
import org.jspecify.annotations.NullMarked;

/**
 * A player took a posture, or came out of one.
 *
 * <p>One event for both ends of a session, because the pair is what a listener needs: the same session that started
 * is the one that ends, and the return position is what puts the player back where they were.
 */
@NullMarked
public final class UxmPoseEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean started;
    private final UxmPoseType type;
    private final UxmLocation returnLocation;
    private final Optional<UUID> targetId;
    private final Instant startedAt;

    public UxmPoseEvent(
            UUID playerId,
            String playerName,
            boolean started,
            UxmPoseType type,
            UxmLocation returnLocation,
            Optional<UUID> targetId,
            Instant startedAt) {
        super(playerId, playerName);
        this.started = started;
        this.type = Objects.requireNonNull(type, "type");
        this.returnLocation = Objects.requireNonNull(returnLocation, "returnLocation");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    /** Whether the pose is beginning rather than ending. */
    public boolean isStarted() {
        return started;
    }

    /** Which posture. */
    public UxmPoseType getType() {
        return type;
    }

    /** Where the player stood before the pose, and where they are put back. */
    public UxmLocation getReturnLocation() {
        return returnLocation;
    }

    /** The player being sat on, present only for {@link UxmPoseType#PLAYER_SIT}. */
    public Optional<UUID> getTargetId() {
        return targetId;
    }

    /** When the session began, which is this moment on a start and earlier on an end. */
    public Instant getStartedAt() {
        return startedAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
