package com.uxplima.uxmessentials.economy.domain;

/** Where a sweep over every account stands in its journal. */
public enum SweepState {
    /** Being worked through, a chunk at a time. */
    RUNNING,
    /** A chunk failed for a reason that passes (a lost connection, a lock cycle); it is tried again. */
    PAUSED,
    /** Every account it reaches was done. */
    DONE,
    /** An operator stopped it; the chunks done before stay done. */
    CANCELLED,
    /** It cannot go on: the database refused its rows, or its currency is no longer configured. */
    FAILED;

    /** Whether the sweep may still change accounts, which keeps any other sweep of its currency from opening. */
    public boolean unfinished() {
        return this == RUNNING || this == PAUSED;
    }
}
