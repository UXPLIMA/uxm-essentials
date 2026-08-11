package com.uxplima.uxmessentials.api.query;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmSecurityStatus;

/**
 * Whether an account is protected by a second factor, and whether it is locked out.
 *
 * <p>Enough for a staff panel to show who has enrolled and to explain why somebody cannot get in. Never the factor
 * itself: no PIN, no authenticator secret, no recovery material. Those exist so that only the account holder can
 * present them, and an API that handed them out would be handing out the account.
 */
public interface UxmSecurityQuery {

    /** What is on file for this account, and whether it is inside a lockout window right now. */
    CompletableFuture<UxmSecurityStatus> of(UUID playerId);

    /**
     * Whether the account is locked out as of now.
     *
     * <p>Answered on the calling thread: the lockout window is in memory, because it is per-run state rather than a
     * record. A lockout the operator chose to write to the ban list is a ban as well, and reads as one there.
     */
    boolean isLockedOut(UUID playerId);
}
