package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What the server currently knows about a player being at their keyboard.
 *
 * <p>Presence is tracked for online players only and is never written down, so this describes a player who is
 * logged in right now. A player who typed {@code /afk} has a reason, one the idle sweep flagged does not.
 *
 * @param playerId the player
 * @param afk whether they are away, whether they said so themselves or the idle sweep decided it
 * @param afkReason the reason they gave, or empty for an automatic flag and for a player who is not away
 * @param lastActivity when they last moved, chatted or ran a command
 */
public record UxmPresence(UUID playerId, boolean afk, Optional<String> afkReason, Instant lastActivity) {

    public UxmPresence {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(afkReason, "afkReason");
        Objects.requireNonNull(lastActivity, "lastActivity");
    }
}
