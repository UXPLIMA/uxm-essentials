package com.uxplima.uxmessentials.api.view;

/**
 * Who may use a player warp.
 *
 * <p>Orthogonal to {@link UxmPlayerWarpStatus}: a warp can be public and still suspended, in which case nobody
 * reaches it.
 */
public enum UxmPlayerWarpAccess {

    /** Anyone may use it. */
    PUBLIC,

    /** Anyone who knows the password may use it. */
    PASSWORD,

    /** Only the players the owner listed may use it. */
    WHITELIST,

    /** Only the owner may use it. */
    PRIVATE
}
