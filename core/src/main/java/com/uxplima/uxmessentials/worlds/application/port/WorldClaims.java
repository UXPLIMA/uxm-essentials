package com.uxplima.uxmessentials.worlds.application.port;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Copies a world no backend has claimed yet onto this backend. A port because the claim is one conditional write to
 * the shared database, and because the copied row has to reach this backend's cache and its peers once it is written.
 */
@FunctionalInterface
public interface WorldClaims {

    /**
     * Off-tick: give this backend its own copy of the unclaimed world {@code name} and its settings, leaving the
     * unclaimed row for other backends with the same world. False when no unclaimed world has that name, or this
     * backend already runs a world of that name.
     */
    boolean claim(WorldName name);
}
