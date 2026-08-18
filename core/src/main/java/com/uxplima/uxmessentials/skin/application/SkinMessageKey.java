package com.uxplima.uxmessentials.skin.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The skin context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code SKIN_APPLIED} to {@code skin.applied}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context.
 *
 * <p>Per the i18n contract a disabled module still ships its keys, so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum SkinMessageKey implements MessageKey {

    // The change itself: what a player is told when a skin goes on, comes off, or is refreshed.
    SKIN_APPLIED("skin.applied"),
    SKIN_CLEARED("skin.cleared"),
    SKIN_NOTHING_TO_CLEAR("skin.nothing-to-clear"),
    SKIN_UPDATED("skin.updated"),
    SKIN_NOTHING_STORED("skin.nothing-stored"),
    SKIN_WORKING("skin.working"),

    // Refusals: each of the five rules a set can break, plus the two ways a lookup can come back with nothing.
    SKIN_SOURCE_DISABLED("skin.source-disabled"),
    SKIN_BLOCKED("skin.blocked"),
    SKIN_URL_NOT_ALLOWED("skin.url-not-allowed"),
    SKIN_NO_PERMISSION("skin.no-permission"),
    SKIN_ON_COOLDOWN("skin.on-cooldown"),
    SKIN_NOT_FOUND("skin.not-found"),
    SKIN_LOOKUP_FAILED("skin.lookup-failed"),

    // Staff verbs: dressing somebody else, dropping a stored skin, and forgetting a cached one.
    SKIN_SET_FOR_OTHER("skin.set-for-other"),
    SKIN_CLEARED_FOR_OTHER("skin.cleared-for-other"),
    SKIN_DROPPED("skin.dropped"),
    SKIN_PURGED("skin.purged"),
    SKIN_UNKNOWN_PLAYER("skin.unknown-player"),

    // The /skin info read-out: the header and the three lines under it.
    SKIN_INFO_HEADER("skin.info.header"),
    SKIN_INFO_SOURCE("skin.info.source"),
    SKIN_INFO_MODEL("skin.info.model"),
    SKIN_INFO_APPLIED("skin.info.applied"),
    SKIN_INFO_NONE("skin.info.none"),

    // The usage line, shown to a sender who typed something the tree could not place.
    SKIN_USAGE("skin.usage");

    private final String key;

    SkinMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
