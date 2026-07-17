package com.uxplima.uxmessentials.security.application;

/** The outcome of {@code /2fa disable}: the factor was removed, the player was not enrolled, or the proof was wrong. */
public enum DisableResult {

    /** A current factor was proven and the whole registration was removed. */
    DISABLED,

    /** The player held no factor to begin with. */
    NOT_ENROLLED,

    /** The submitted code or PIN did not verify against a current factor; nothing was removed. */
    INVALID_FACTOR
}
