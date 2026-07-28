package com.uxplima.uxmessentials.security.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * What happens to an online player at the moment staff take their second factor away.
 *
 * <p>{@code /security reset} is the recovery door: the one path that clears a factor without proving it, for a player
 * who has lost their authenticator or forgotten their PIN. It is also, from the other side, the path an account theft
 * would want, which is why what it does to a player who is standing in the world right now is a decision an operator
 * should get to make rather than one buried in the code.
 *
 * <p>Leaving them alone is defensible on a server where a reset is routine help-desk work. Sending them back through
 * verification is the answer that matches what a reset means on a server that requires a PIN, since the player is
 * immediately asked to set a new one. Kicking is the strictest reading: nothing about the session survives the
 * revocation, and their next join runs the whole decision from scratch.
 */
public enum RevokedAccess {

    /** Leave them playing. A reset is help-desk work and the session it interrupts is not suspect. */
    NOTHING,

    /**
     * Put them straight back through verification. With a factor still held they prove it again; with none left and
     * the server requiring a PIN they are shown the create pad, so a reset ends with a factor rather than without one.
     */
    REVERIFY,

    /** Disconnect them, so nothing about the revoked session carries on and the next join decides afresh. */
    KICK;

    /** The policy named by {@code raw}, or {@link #REVERIFY} when it names nothing recognisable. */
    public static RevokedAccess parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "nothing", "none" -> NOTHING;
            case "kick" -> KICK;
            default -> REVERIFY;
        };
    }
}
