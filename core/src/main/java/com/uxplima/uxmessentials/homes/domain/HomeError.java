package com.uxplima.uxmessentials.homes.domain;

import com.uxplima.uxmessentials.homes.application.HomesMessageKey;

/**
 * The modelled failures a home operation can produce. Each value carries the {@link HomesMessageKey} the
 * command adapter renders, so a use case returns a {@code Result.err(HomeError.X)} and the caller never
 * re-derives the message — the error carries it, and the failure reason and its localized text never
 * drift apart.
 */
public enum HomeError {

    /** {@code /sethome} (a new name) past the owner's resolved {@code uxmessentials.home.limit.<n>}. */
    LIMIT_REACHED(HomesMessageKey.HOME_LIMIT_REACHED),

    /** A name the owner has no home under: {@code /home}, {@code /delhome}, {@code /movehome}, rename source. */
    NOT_FOUND(HomesMessageKey.HOME_NOT_FOUND),

    /** {@code /renamehome} to a name the owner already has a home under. */
    NAME_TAKEN(HomesMessageKey.HOME_NAME_TAKEN),

    /** The owner has no homes at all when one was required. */
    NONE_SET(HomesMessageKey.HOME_NONE);

    private final HomesMessageKey messageKey;

    HomeError(HomesMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public HomesMessageKey messageKey() {
        return messageKey;
    }
}
