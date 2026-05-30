package com.uxplima.uxmessentials.warps.domain;

import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;

/**
 * The modelled failures a warp operation can produce. Each value carries the {@link WarpsMessageKey} the
 * command adapter renders, so a use case returns a {@code Result.err(WarpError.X)} and the caller never
 * re-derives the message — the error carries it, and the failure reason and its localized text never
 * drift apart.
 */
public enum WarpError {

    /** A name no warp exists under: {@code /warp}, {@code /delwarp}, {@code /movewarp}, {@code /warpinfo}. */
    NOT_FOUND(WarpsMessageKey.WARP_NOT_FOUND),

    /** {@code /setwarp} for a name a warp already exists under, when a re-anchor was not intended. */
    NAME_TAKEN(WarpsMessageKey.WARP_NAME_TAKEN),

    /** No warps exist at all when one was required ({@code /warps} renders its own empty notice). */
    NONE_SET(WarpsMessageKey.WARP_NONE),

    /** The player lacks the warp's required permission (the per-warp node or its extra gate). */
    NO_PERMISSION(WarpsMessageKey.WARP_NO_PERMISSION),

    /** The player cannot pay the warp's cost (only reachable when an economy provider is present). */
    CANNOT_AFFORD(WarpsMessageKey.WARP_CANNOT_AFFORD);

    private final WarpsMessageKey messageKey;

    WarpError(WarpsMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public WarpsMessageKey messageKey() {
        return messageKey;
    }
}
