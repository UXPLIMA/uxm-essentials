package com.uxplima.uxmessentials.worlds.application.port;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Takes a world no backend has claimed yet onto this backend. A port because the claim is one conditional write to
 * the shared database that must not race another backend's claim, and because the claimed row has to reach this
 * backend's cache and its peers once it moves.
 */
@FunctionalInterface
public interface WorldClaims {

    /**
     * Off-tick: record this backend as the server of the unclaimed world {@code name}. False when no unclaimed world
     * has that name, or this backend already runs a world of that name.
     */
    boolean claim(WorldName name);
}
