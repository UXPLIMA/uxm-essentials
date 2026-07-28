package com.uxplima.uxmessentials.security.domain;

import java.util.Locale;

/**
 * What happens to a joining player when the verification decision itself fails: the database is unreachable, the
 * key-file cannot be read, or the lookup throws for any other reason. Someone has to decide, because the join
 * freeze is applied optimistically before the decision runs, and a decision that never lands leaves the player
 * frozen with no keypad, no message and no way forward.
 *
 * <p>Neither answer is free, so it is an operator choice. {@link #KICK} keeps the account door shut and tells the
 * player to try again, at the cost of nobody enrolled being able to play while the database is down. {@link #ALLOW}
 * keeps the server playable and accepts that, for the length of the outage, an enrolled account is protected by
 * nothing but its password.
 */
public enum SafetyNet {

    /** Refuse the join with a "verification is unavailable" message. The default: a closed door fails safe. */
    KICK,

    /** Lift the freeze and let the player in unverified, keeping the server playable through a database outage. */
    ALLOW;

    /** The policy named by {@code raw}, falling back to {@link #KICK} so a typo never opens the door. */
    public static SafetyNet parse(String raw) {
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "allow", "let-in", "letin", "open", "pass" -> ALLOW;
            default -> KICK;
        };
    }
}
