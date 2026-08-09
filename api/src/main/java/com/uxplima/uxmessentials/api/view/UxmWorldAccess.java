package com.uxplima.uxmessentials.api.view;

/** Why a player was let into a world, or was not. */
public enum UxmWorldAccess {

    /** The player may enter. */
    ALLOWED,

    /** The world requires a permission the player does not hold. */
    DENIED_PERMISSION,

    /** The world is at its player cap. */
    DENIED_FULL
}
