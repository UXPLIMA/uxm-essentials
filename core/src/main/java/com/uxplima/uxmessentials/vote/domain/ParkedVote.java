package com.uxplima.uxmessentials.vote.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A vote for a name no account answers to yet, kept until a player with that name first joins.
 *
 * @param name the name the vote site sent, as it was spelled
 * @param serviceName the vote list the vote came from
 * @param at when the vote was received
 * @param exactCase whether only a player spelled exactly like {@code name} may claim it: on an offline-mode server
 *     other spellings of the name were already separate accounts when the vote came, so a different case is a
 *     different player
 */
public record ParkedVote(String name, String serviceName, Instant at, boolean exactCase) {

    public ParkedVote {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(serviceName, "serviceName");
        Objects.requireNonNull(at, "at");
    }
}
