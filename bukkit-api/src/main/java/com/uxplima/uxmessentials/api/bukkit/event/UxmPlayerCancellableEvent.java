package com.uxplima.uxmessentials.api.bukkit.event;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import org.jspecify.annotations.NullMarked;

/** A vetoable action taken by, or on, one player. */
@NullMarked
public abstract class UxmPlayerCancellableEvent extends UxmCancellableEvent {

    private final UUID subjectId;
    private final String subjectName;

    protected UxmPlayerCancellableEvent(UUID subjectId, String subjectName) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.subjectName = Objects.requireNonNull(subjectName, "subjectName");
    }

    /** The id of the player whose action this is. Always present, online or not. */
    public UUID getPlayerId() {
        return subjectId;
    }

    /** The name of the player whose action this is, as uxmEssentials last knew it. */
    public String getPlayerName() {
        return subjectName;
    }

    /**
     * The player whose action this is, as an offline handle. There is deliberately no live {@code Player} accessor:
     * this event is fired off the tick thread, where acting on a live player is unsafe.
     */
    public OfflinePlayer getOfflinePlayer() {
        return Bukkit.getOfflinePlayer(subjectId);
    }
}
