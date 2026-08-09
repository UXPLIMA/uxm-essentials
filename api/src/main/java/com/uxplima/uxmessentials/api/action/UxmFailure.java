package com.uxplima.uxmessentials.api.action;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * Why an action did not happen.
 *
 * <p>The {@link #code()} is the part to branch on. It is a published constant, stable across releases, and the
 * constants on this class are the whole set: an implementation may not invent one, and a drift guard enforces it.
 * The {@link #message()} is English, meant for your log line, and may be reworded at any time.
 *
 * <p>A failure is an outcome and not an error. The action was understood, was attempted, and the server said no:
 * the player is already banned, the name is taken, the wallet is short. A malformed call (a null id, a negative
 * amount) throws instead, because that is a bug in the caller rather than an answer.
 *
 * @param code the stable machine-readable reason
 * @param message an English explanation for logs
 */
@NullMarked
public record UxmFailure(String code, String message) {

    /** A listener refused the operation by cancelling its {@code Pre} event. */
    public static final String CANCELLED = "cancelled";

    /** The thing named does not exist: no such home, warp, kit, world or currency. */
    public static final String NOT_FOUND = "not-found";

    /** Something with that name already exists and this operation will not overwrite it. */
    public static final String ALREADY_EXISTS = "already-exists";

    /** The operation needs the player online and they are not. */
    public static final String PLAYER_OFFLINE = "player-offline";

    /** The wallet does not hold what the operation would take out of it. */
    public static final String INSUFFICIENT_FUNDS = "insufficient-funds";

    /** The target is already in the state the operation would put them in. */
    public static final String ALREADY_IN_STATE = "already-in-state";

    /** A gate the player-facing command applies refused: a permission, a cooldown, a claim already taken. */
    public static final String REFUSED = "refused";

    /** The operation reached the database or the world and it failed. The message says what. */
    public static final String FAILED = "failed";

    public UxmFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    /** A failure with this code and message. */
    public static UxmFailure of(String code, String message) {
        return new UxmFailure(code, message);
    }

    /** Whether this failure carries {@code candidate} as its code, which is the usual way to branch. */
    public boolean is(String candidate) {
        return code.equals(Objects.requireNonNull(candidate, "candidate"));
    }
}
