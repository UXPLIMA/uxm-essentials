package com.uxplima.uxmessentials.security.application;

/** The outcome of {@code /pin remove}: the PIN was removed, none was set, the proof was wrong, or the account is locked. */
public enum PinRemoveResult {

    /** The current PIN was proven and removed; any authenticator factor the player holds is untouched. */
    REMOVED,

    /** The player had no PIN to remove. */
    NOT_SET,

    /** The submitted PIN did not match the stored one; nothing was removed. */
    INVALID_PIN,

    /** The account is on the shared lockout after too many failed proofs; the attempt was refused outright. */
    LOCKED_OUT
}
