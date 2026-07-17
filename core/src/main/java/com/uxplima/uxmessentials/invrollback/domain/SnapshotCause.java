package com.uxplima.uxmessentials.invrollback.domain;

/**
 * Why a snapshot was taken. The cause is a first-class, queryable fact stored alongside the snapshot (as the
 * constant {@link #name()}) so the restore GUI can label each entry and staff can tell a death snapshot apart
 * from a routine logout one.
 */
public enum SnapshotCause {

    /** The player's inventory was frozen because they died (captured before the items drop). */
    DEATH,

    /** The player's inventory was frozen because they left the server. */
    LOGOUT,

    /**
     * The player's current inventory was frozen as a safety copy immediately before it was overwritten by a
     * restore, so an accidental restore can itself be undone. Reserved for the Phase 2 restore flow.
     */
    RESTORE
}
