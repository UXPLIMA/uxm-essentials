package com.uxplima.uxmessentials.playerwarps.domain;

import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;

/**
 * The modelled failures a player-warp operation can produce. Each value carries the
 * {@link PlayerwarpsMessageKey} the command adapter renders, so a use case returns a
 * {@code Result.err(PlayerWarpError.X)} and the caller never re-derives the message — the error carries it,
 * and the failure reason and its localized text never drift apart.
 */
public enum PlayerWarpError {

    /** A name no warp exists under for the owner: {@code /pwarp}, {@code /pwarp del}, the visibility toggles. */
    NOT_FOUND(PlayerwarpsMessageKey.PWARP_NOT_FOUND),

    /** {@code /setpwarp} for a name the owner already has, when a re-anchor was not intended. */
    NAME_TAKEN(PlayerwarpsMessageKey.PWARP_NAME_TAKEN),

    /** {@code /setpwarp} when the owner is already at their resolved warp limit. */
    LIMIT_REACHED(PlayerwarpsMessageKey.PWARP_LIMIT_REACHED),

    /** The owner has no player-warps at all when one was required. */
    NONE_SET(PlayerwarpsMessageKey.PWARP_NONE),

    /** A non-owner tried to use a warp that is still private. */
    NOT_PUBLIC(PlayerwarpsMessageKey.PWARP_NOT_PUBLIC),

    /** The warp is not {@link com.uxplima.uxmessentials.playerwarps.domain.WarpStatus#ACTIVE active} — it is
     * suspended or archived, so nobody (not even the owner) may use it until its status is settled. */
    SUSPENDED(PlayerwarpsMessageKey.PWARP_SUSPENDED),

    /** The actor is banned from this warp and holds no ban bypass. */
    BANNED(PlayerwarpsMessageKey.PWARP_BANNED),

    /** A non-member tried to use a whitelist-access warp they are not on the whitelist for. */
    NOT_WHITELISTED(PlayerwarpsMessageKey.PWARP_NOT_WHITELISTED),

    /** Too many wrong password attempts in a row — the per-warp attempt cooldown is still cooling down. */
    RATE_LIMITED(PlayerwarpsMessageKey.PWARP_RATE_LIMITED),

    /** The entry charge did not complete (the payer could not pay, or the accrual failed), so no visit occurred. */
    CANNOT_AFFORD(PlayerwarpsMessageKey.PWARP_CANNOT_AFFORD),

    /** The warp destination is physically unsafe (lava, void, suffocation). */
    UNSAFE_LOCATION(PlayerwarpsMessageKey.PWARP_UNSAFE),

    /** The warp is locked. */
    LOCKED(PlayerwarpsMessageKey.PWARP_LOCKED),

    /** The player entered the wrong password. */
    WRONG_PASSWORD(PlayerwarpsMessageKey.PWARP_WRONG_PASSWORD),

    /** The warp cannot be created in this world because it is blacklisted. */
    WORLD_BLACKLISTED(PlayerwarpsMessageKey.PWARP_WORLD_BLACKLISTED),

    /** The actor holds no role, or too low a role, to perform this management action on the warp. */
    NO_PERMISSION(PlayerwarpsMessageKey.PWARP_NO_PERMISSION),

    /** A price currency change was refused because the warp's earnings bank is not empty — withdraw it first. */
    CURRENCY_LOCKED(PlayerwarpsMessageKey.PWARP_CURRENCY_LOCKED);

    private final PlayerwarpsMessageKey messageKey;

    PlayerWarpError(PlayerwarpsMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public PlayerwarpsMessageKey messageKey() {
        return messageKey;
    }
}
