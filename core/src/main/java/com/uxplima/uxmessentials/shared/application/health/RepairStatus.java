package com.uxplima.uxmessentials.shared.application.health;

/** The outcome category of one explicitly confirmed {@code /uxmess doctor repair}. */
public enum RepairStatus {
    /** The data was already consistent, so the task changed nothing. */
    UNCHANGED,

    /** The task repaired one or more safe-to-remove inconsistencies. */
    REPAIRED,

    /** The repair failed and its transaction was rolled back. */
    FAILED
}
