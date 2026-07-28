package com.uxplima.uxmessentials.security.application;

/**
 * The outcome of {@code /pin change}: the PIN was replaced, or it was refused because there was nothing to change,
 * the current PIN was wrong, the account is locked out, or the replacement failed the policy.
 */
public enum PinChangeResult {

    /** The current PIN was proven and the replacement stored. */
    CHANGED,

    /** The player had no PIN to change; they set a first one with {@code /pin set}. */
    NOT_SET,

    /** The submitted current PIN did not match the stored one; the replacement was never looked at. */
    INVALID_PIN,

    /** The account is on the shared lockout after too many failed proofs; the attempt was refused outright. */
    LOCKED_OUT,

    /** The replacement had fewer digits than the policy's minimum; the current PIN stands. */
    TOO_SHORT,

    /** The replacement had more digits than the policy's maximum; the current PIN stands. */
    TOO_LONG,

    /** The replacement contained a non-digit character; the current PIN stands. */
    NOT_NUMERIC
}
