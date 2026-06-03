package com.uxplima.uxmessentials.playerwarps.domain;

import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;

/**
 * The modelled failures a player-warp operation can produce. Each value carries the
 * {@link PlayerwarpsMessageKey} the command adapter renders, so a use case returns a
 * {@code Result.err(PlayerWarpError.X)} and the caller never re-derives the message — the error carries it,
 * and the failure reason and its localized text never drift apart.
 */
public enum PlayerWarpError {

    /** A name no warp exists under for the owner: {@code /pwarp}, {@code /delpwarp}, the visibility toggles. */
    NOT_FOUND(PlayerwarpsMessageKey.PWARP_NOT_FOUND),

    /** {@code /setpwarp} for a name the owner already has, when a re-anchor was not intended. */
    NAME_TAKEN(PlayerwarpsMessageKey.PWARP_NAME_TAKEN),

    /** {@code /setpwarp} when the owner is already at their resolved warp limit. */
    LIMIT_REACHED(PlayerwarpsMessageKey.PWARP_LIMIT_REACHED),

    /** The owner has no player-warps at all when one was required. */
    NONE_SET(PlayerwarpsMessageKey.PWARP_NONE),

    /** A non-owner tried to use a warp that is still private. */
    NOT_PUBLIC(PlayerwarpsMessageKey.PWARP_NOT_PUBLIC);

    private final PlayerwarpsMessageKey messageKey;

    PlayerWarpError(PlayerwarpsMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public PlayerwarpsMessageKey messageKey() {
        return messageKey;
    }
}
