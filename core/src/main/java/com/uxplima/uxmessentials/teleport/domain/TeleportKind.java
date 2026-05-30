package com.uxplima.uxmessentials.teleport.domain;

/**
 * The reason a teleport is happening, fixed at the point the flow begins.
 *
 * <p>The kind drives three orthogonal decisions the domain otherwise could not infer from the
 * destination alone: which {@code uxmessentials.<feature>} permission tier sizes the warmup and
 * cooldown, whether the move triggered a {@code /back} capture worth recording, and which audit verb
 * the adapter writes. A staff {@code /tp} is not the same event as a player {@code /rtp}, even when both
 * land at the same coordinates.
 */
public enum TeleportKind {

    /** A {@code /tpa} or {@code /tpahere} handshake, after the target accepted. */
    REQUEST,

    /** A {@code /back} return to a captured location. */
    BACK,

    /** A {@code /rtp} random teleport served from the safe-location queue. */
    RANDOM,

    /** A {@code /spawn} to a resolved (possibly mirrored) spawn point. */
    SPAWN,

    /** A respawn placement resolved through the per-world respawn chain. */
    RESPAWN,

    /** A staff direct teleport ({@code /tp}, {@code /tphere}, {@code /tppos}, {@code /tpoffline}). */
    ADMIN,

    /** A vertical or line-of-sight positional verb ({@code /top}, {@code /bottom}, {@code /jump}, {@code /up}). */
    POSITIONAL
}
