package com.uxplima.uxmessentials.moderation.application;

/**
 * How an escalation rung landed against the sanction of its kind the player was already under. A rung is meant
 * to leave the player under at least its own sanction, never under less, so a running sanction that already
 * lasts as long or longer is kept rather than cut short.
 */
public enum EscalationOutcome {

    /** Nothing of this kind was in force, so the rung's sanction now is. */
    APPLIED,

    /** A shorter sanction of this kind was in force, and the rung's longer one replaced it. */
    EXTENDED,

    /** A sanction of this kind lasting as long or longer was already in force, so it was left as it was. */
    ALREADY_STRONGER
}
