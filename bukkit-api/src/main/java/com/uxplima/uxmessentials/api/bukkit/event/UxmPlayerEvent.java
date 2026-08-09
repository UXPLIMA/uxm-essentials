package com.uxplima.uxmessentials.api.bukkit.event;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A notification event about one player.
 *
 * <p>The player is named by id rather than by handle, because the player an event concerns need not be online: a mail
 * delivery or a moderation action reaches an offline account just as well. {@link #getPlayer()} is the convenient
 * form when your listener only cares about online ones.
 */
@NullMarked
public abstract class UxmPlayerEvent extends UxmEvent {

    private final UUID subjectId;
    private final String subjectName;

    protected UxmPlayerEvent(UUID subjectId, String subjectName) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.subjectName = Objects.requireNonNull(subjectName, "subjectName");
    }

    /** The id of the player this event is about. Always present, online or not. */
    public UUID getPlayerId() {
        return subjectId;
    }

    /** The name of the player this event is about, as uxmEssentials last knew it. */
    public String getPlayerName() {
        return subjectName;
    }

    /** The player this event is about, or {@code null} when they are offline. */
    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(subjectId);
    }

    /** The player this event is about as an offline handle, which is never {@code null}. */
    public OfflinePlayer getOfflinePlayer() {
        return Bukkit.getOfflinePlayer(subjectId);
    }
}
