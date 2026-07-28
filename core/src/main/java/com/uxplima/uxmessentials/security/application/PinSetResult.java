package com.uxplima.uxmessentials.security.application;

/**
 * The outcome of {@code /pin set}: the PIN was hashed and stored, or refused with the reason. A player who already
 * holds a PIN is turned away here and replaces it through {@code /pin change} instead.
 */
public enum PinSetResult {

    /** The PIN passed the policy and was hashed and stored as the player's PIN factor. */
    SET,

    /** The player already holds a PIN; replacing it needs the current one, through {@code /pin change}. */
    ALREADY_SET,

    /** The PIN had fewer digits than the policy's minimum. */
    TOO_SHORT,

    /** The PIN had more digits than the policy's maximum. */
    TOO_LONG,

    /** The PIN contained a non-digit character. */
    NOT_NUMERIC
}
