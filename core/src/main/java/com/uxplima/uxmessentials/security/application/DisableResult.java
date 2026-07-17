package com.uxplima.uxmessentials.security.application;

/**
 * The outcome of {@code /2fa disable}: the factor was removed, the player was not enrolled, the proof was wrong, or the
 * account is locked out after too many failed proofs on the shared brute-force budget.
 */
public enum DisableResult {

    /** A current factor was proven and the whole registration was removed. */
    DISABLED,

    /** The player held no factor to begin with. */
    NOT_ENROLLED,

    /** The submitted code or PIN did not verify against a current factor; nothing was removed. */
    INVALID_FACTOR,

    /** The account is on the shared lockout after too many failed proofs; the attempt was refused outright. */
    LOCKED_OUT
}
