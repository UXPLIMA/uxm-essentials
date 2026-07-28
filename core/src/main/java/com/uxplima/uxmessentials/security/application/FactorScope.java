package com.uxplima.uxmessentials.security.application;

/**
 * Which of a player's factors an operator reset targets. The three are spelled out rather than inferred so
 * {@code /security reset <player> totp} can never take a PIN with it: an operator clearing a lost authenticator
 * should not silently strip a PIN the player still knows.
 */
public enum FactorScope {

    /** Only the authenticator (TOTP) factor. */
    TOTP,

    /** Only the numeric PIN factor. */
    PIN,

    /** Both factors, leaving the player unenrolled. */
    ALL
}
