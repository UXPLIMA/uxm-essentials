package com.uxplima.uxmessentials.homes.domain;

import com.uxplima.uxmessentials.homes.application.HomesMessageKey;

/**
 * The modelled failures a home operation can produce. Each value carries the {@link HomesMessageKey} the
 * command adapter renders, so a use case returns a {@code Result.err(HomeError.X)} and the caller never
 * re-derives the message — the error carries it, and the failure reason and its localized text never
 * drift apart.
 */
public enum HomeError {

    /** {@code /sethome} into a new slot past the owner's resolved {@code uxmessentials.home.limit.<n>}. */
    LIMIT_REACHED(HomesMessageKey.HOME_LIMIT_REACHED),

    /** A slot the owner has no home in: teleport, relocate, relabel, re-icon, or delete of an empty slot. */
    NOT_FOUND(HomesMessageKey.HOME_NOT_FOUND),

    /** {@code /sethome} into a slot the owner already has a home in. */
    SLOT_TAKEN(HomesMessageKey.HOME_SLOT_TAKEN),

    /** A slot index at or above the owner's resolved maximum-slot count. */
    SLOT_OUT_OF_RANGE(HomesMessageKey.HOME_SLOT_OUT_OF_RANGE);

    private final HomesMessageKey messageKey;

    HomeError(HomesMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public HomesMessageKey messageKey() {
        return messageKey;
    }
}
