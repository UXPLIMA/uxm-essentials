package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One line of a player's moderation history, whether or not it still stands.
 *
 * <p>Unlike {@link UxmSanction}, this is the record of something that happened: a ban that was later lifted is
 * two lines here and no sanction at all.
 *
 * @param action what was done
 * @param playerId who it was done to
 * @param actor who did it
 * @param reason the reason given, or empty when none was
 * @param at when it happened
 * @param expiry when the punishment was set to lapse, or empty when it was permanent or does not lapse
 */
public record UxmSanctionRecord(
        UxmSanctionAction action,
        UUID playerId,
        UxmIssuer actor,
        Optional<String> reason,
        Instant at,
        Optional<Instant> expiry) {

    public UxmSanctionRecord {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(expiry, "expiry");
    }
}
