package com.uxplima.uxmessentials.servertweaks.domain;

import java.util.Objects;

/**
 * Which of a player's two signed input streams a SignedVelocity directive applies to: their public chat or their
 * commands. The proxy keeps the two apart because a chat directive and a command directive can be in flight for the
 * same player at once, and each must be matched back to its own event.
 *
 * <p>The wire tokens ({@code CHAT_RESULT}, {@code COMMAND_RESULT}) are fixed by the SignedVelocity plugin-message
 * protocol this backend interoperates with; {@link #fromWire(String)} maps a received token to the enum and rejects
 * anything else so a malformed frame is caught rather than silently mishandled.
 */
public enum SignedSource {
    CHAT("CHAT_RESULT"),
    COMMAND("COMMAND_RESULT");

    private final String wireToken;

    SignedSource(String wireToken) {
        this.wireToken = wireToken;
    }

    /** The exact token the SignedVelocity protocol uses for this source on the wire. */
    public String wireToken() {
        return wireToken;
    }

    /**
     * Resolve the source from its protocol token.
     *
     * @param token the {@code source} field read from a SignedVelocity frame
     * @return the matching source
     * @throws IllegalArgumentException if the token is not a known SignedVelocity source
     */
    public static SignedSource fromWire(String token) {
        Objects.requireNonNull(token, "token");
        for (SignedSource source : values()) {
            if (source.wireToken.equals(token)) {
                return source;
            }
        }
        throw new IllegalArgumentException("unknown SignedVelocity source: " + token);
    }
}
