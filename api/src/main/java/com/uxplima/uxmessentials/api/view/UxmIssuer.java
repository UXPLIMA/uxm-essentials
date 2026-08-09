package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Who handed down a moderation action.
 *
 * <p>The id is optional because the console has no account, and because a punishment issued years ago outlives the
 * staff member who issued it. The name is always there, which is what a listener would display.
 *
 * @param uuid the staff member's id, or empty when the console issued it
 * @param name the issuer's name as recorded at the time
 */
public record UxmIssuer(Optional<UUID> uuid, String name) {

    public UxmIssuer {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
    }

    /** Whether this was the console rather than a player. */
    public boolean isConsole() {
        return uuid.isEmpty();
    }
}
