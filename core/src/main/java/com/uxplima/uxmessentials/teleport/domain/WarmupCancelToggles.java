package com.uxplima.uxmessentials.teleport.domain;

/**
 * The per-axis warmup cancel toggles from {@code teleport.conf}: which player actions abort a pending
 * warmup. Move-cancel is conceptually always on (the move-cancels-warmup invariant), but an operator may
 * additionally make rotation, damage, or interaction cancel the warmup. Modelled as a value object so the
 * cancel decision in {@link PendingTeleport} is a pure function of configuration plus the observed action.
 *
 * <p>{@code moveThreshold} softens the move axis: a player who drifts less than this many blocks from the
 * origin (mouse jitter, standing on a moving boat) does not lose the warmup. A threshold of {@code 0}
 * keeps the historical block-cell behaviour, where leaving the origin block at all cancels.
 *
 * @param cancelOnMove whether leaving the origin aborts the warmup (the invariant; default true)
 * @param cancelOnRotate whether turning the head aborts the warmup
 * @param cancelOnDamage whether taking damage aborts the warmup
 * @param cancelOnInteract whether using an item or block aborts the warmup
 * @param moveThreshold the distance in blocks a player may drift before the move axis cancels (0 = block cell)
 */
public record WarmupCancelToggles(
        boolean cancelOnMove,
        boolean cancelOnRotate,
        boolean cancelOnDamage,
        boolean cancelOnInteract,
        double moveThreshold) {

    public WarmupCancelToggles {
        if (!Double.isFinite(moveThreshold) || moveThreshold < 0) {
            throw new IllegalArgumentException("moveThreshold must be finite and non-negative: " + moveThreshold);
        }
    }

    /** The canonical default: move cancels on a block-cell change, rotation, damage and interact do not. */
    public static WarmupCancelToggles defaults() {
        return new WarmupCancelToggles(true, false, false, false, 0.0);
    }

    /** Whether the given cancel axis is armed under these toggles. */
    public boolean arms(WarmupCancelReason reason) {
        return switch (reason) {
            case MOVED -> cancelOnMove;
            case ROTATED -> cancelOnRotate;
            case DAMAGED -> cancelOnDamage;
            case INTERACTED -> cancelOnInteract;
            case ABORTED -> true;
        };
    }

    /** True when a drift of {@code distance} blocks from the origin is large enough to cancel the warmup. */
    public boolean movePassesThreshold(double distance) {
        return distance > moveThreshold;
    }
}
