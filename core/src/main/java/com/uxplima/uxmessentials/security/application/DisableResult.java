package com.uxplima.uxmessentials.security.application;

/**
 * The outcome of {@code /2fa disable}: the authenticator factor was removed, the player had none, the code was wrong,
 * or the account is locked out after too many failed proofs on the shared brute-force budget.
 */
public enum DisableResult {

    /** A current code was proven and the authenticator factor was removed; any PIN the player holds is untouched. */
    DISABLED,

    /** The player held no authenticator factor to begin with. */
    NOT_ENROLLED,

    /** The submitted code did not verify against the stored secret; nothing was removed. */
    INVALID_CODE,

    /** The account is on the shared lockout after too many failed proofs; the attempt was refused outright. */
    LOCKED_OUT
}
