package com.uxplima.uxmessentials.teleport.domain;

/**
 * Why a teleport warmup was cancelled before it elapsed. The per-axis cancel toggles in
 * {@code teleport.conf} decide which of these actually abort a warmup; the move-cancels-warmup
 * invariant is the {@link #MOVED} case and is always honoured.
 */
public enum WarmupCancelReason {

    /** The player's origin block changed, the always-on move-cancels-warmup invariant. */
    MOVED,

    /** The player turned their head and rotation-cancel is enabled. */
    ROTATED,

    /** The player took damage and damage-cancel is enabled. */
    DAMAGED,

    /** The player interacted (used an item or block) and interact-cancel is enabled. */
    INTERACTED,

    /** The warmup was called off with no more specific cause. */
    ABORTED,

    /** The player left the server mid-warmup. */
    QUIT,

    /** The same player started another teleport, which replaced this one. */
    SUPERSEDED
}
