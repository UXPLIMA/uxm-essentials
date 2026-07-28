package com.uxplima.uxmessentials.shared.application.reload;

/**
 * How one step of a {@code /uxmess reload} run ended.
 *
 * <p>The three values are deliberately distinct outcomes rather than a success flag: an operator who edits a file
 * needs to know whether the edit is live now ({@link #APPLIED}), whether it will only take effect after a restart
 * ({@link #RESTART_REQUIRED}), or whether it was rejected outright ({@link #FAILED}). Reporting a restart-bound
 * change as a success is the failure this type exists to prevent.
 */
public enum ReloadStatus {

    /** The step re-read its source and the new values are live. */
    APPLIED,

    /** The step's subsystem cannot be rebuilt at runtime, so the change waits for a server restart. */
    RESTART_REQUIRED,

    /** The step ran and failed, so the previous state is still in force. */
    FAILED;

    /** The more serious of two statuses, ordered {@code APPLIED} then {@code RESTART_REQUIRED} then {@code FAILED}. */
    public ReloadStatus worst(ReloadStatus other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
