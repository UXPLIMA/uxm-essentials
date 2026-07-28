package com.uxplima.uxmessentials.security.application.port;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Turns a verification lockout into a real, durable ban on the server's own ban list.
 *
 * <p>Without this the lockout is an in-memory cooldown: it stops a burst of guesses inside one session and is gone the
 * moment the server restarts, which is not much of an obstacle to somebody who wants to sit and guess a four-digit
 * PIN. The fix is emphatically <em>not</em> a second ban list of this module's own. The plugin already has a ban
 * system, with a history, an unban command, staff notifications and cross-server sync, and a security lockout that
 * quietly kept its own parallel list would mean staff seeing a player barred with nothing in the records to say why.
 * So the lockout goes through the same door every other ban does, as an ordinary tempban that happens to be issued by
 * the server rather than a person.
 *
 * <p>The binding is soft. A server running with the moderation module disabled has no ban list to write to, so the
 * implementation is {@link #NONE} and the lockout stays the in-memory cooldown it always was, rather than the module
 * failing to start over a feature it cannot have.
 */
@FunctionalInterface
public interface LockoutBan {

    /**
     * Bar {@code target} from the server for {@code duration}, recording {@code reason} against it.
     *
     * @return whether a ban was actually applied; false when there is no ban surface to apply it to, or when the ban
     *     surface refused (an exempt target, say), in which case the caller keeps whatever it would have done anyway
     */
    boolean ban(PlayerRef target, Duration duration, String reason);

    /** The no-ban-surface binding: nothing is recorded and the caller falls back to the in-memory lockout alone. */
    LockoutBan NONE = (target, duration, reason) -> false;
}
