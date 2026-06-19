package com.uxplima.uxmessentials.worlds.domain;

/** Why a world-entry teleport was initiated, so access control can apply the right policy. */
public enum WorldTeleportCause {
    SPAWN,
    ADMIN
}
