package com.uxplima.uxmessentials.security.application;

/** The outcome of {@code /pin set}: the PIN was hashed and stored, or refused with the reason it failed the policy. */
public enum PinSetResult {

    /** The PIN passed the policy and was hashed and stored as the player's PIN factor. */
    SET,

    /** The PIN had fewer digits than the policy's minimum. */
    TOO_SHORT,

    /** The PIN had more digits than the policy's maximum. */
    TOO_LONG,

    /** The PIN contained a non-digit character. */
    NOT_NUMERIC
}
