package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.UUID;

/**
 * An account the server knows, under the name it last played under.
 *
 * <p>The uuid is the identity every other query and action takes. It is whatever the server gave the account: a
 * Mojang uuid on an online-mode server, a name-derived one on an offline-mode server, a Floodgate one for a Bedrock
 * player. A consumer should pass it on as it is rather than work one out from the name.
 *
 * @param playerId the account
 * @param name the name it last played under, in its own case; a Bedrock player's carries the Floodgate prefix
 */
public record UxmPlayer(UUID playerId, String name) {

    public UxmPlayer {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(name, "name");
    }
}
