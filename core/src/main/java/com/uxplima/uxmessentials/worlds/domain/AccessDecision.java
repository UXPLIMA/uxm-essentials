package com.uxplima.uxmessentials.worlds.domain;

/** Outcome of evaluating whether a player may enter a world, with the reason when denied. */
public enum AccessDecision {
    ALLOWED,
    DENIED_PERMISSION,
    DENIED_FULL;

    /** True only for {@link #ALLOWED}; every other value is a denial carrying its reason. */
    public boolean allowed() {
        return this == ALLOWED;
    }
}
