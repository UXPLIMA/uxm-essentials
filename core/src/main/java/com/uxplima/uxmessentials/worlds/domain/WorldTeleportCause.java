package com.uxplima.uxmessentials.worlds.domain;

/** Why a world-entry teleport was initiated, so access control can apply the right policy. */
public enum WorldTeleportCause {
    SPAWN,
    ADMIN,
    /** A player fell out of the world and the world's void-rescue chain caught them. */
    VOID_RESCUE
}
