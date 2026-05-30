package com.uxplima.uxmessentials.teleport.domain;

/**
 * The per-axis warmup cancel toggles from {@code teleport.conf}: which player actions abort a pending
 * warmup. Move-cancel is conceptually always on (the move-cancels-warmup invariant), but an operator may
 * additionally make rotation or damage cancel the warmup. Modelled as a value object so the cancel
 * decision in {@link PendingTeleport} is a pure function of configuration plus the observed action.
 *
 * @param cancelOnMove whether leaving the origin block aborts the warmup (the invariant; default true)
 * @param cancelOnRotate whether turning the head aborts the warmup
 * @param cancelOnDamage whether taking damage aborts the warmup
 */
public record WarmupCancelToggles(boolean cancelOnMove, boolean cancelOnRotate, boolean cancelOnDamage) {

    /** The canonical default: move cancels, rotation and damage do not. */
    public static WarmupCancelToggles defaults() {
        return new WarmupCancelToggles(true, false, false);
    }

    /** Whether the given cancel axis is armed under these toggles. */
    public boolean arms(WarmupCancelReason reason) {
        return switch (reason) {
            case MOVED -> cancelOnMove;
            case ROTATED -> cancelOnRotate;
            case DAMAGED -> cancelOnDamage;
            case ABORTED -> true;
        };
    }
}
