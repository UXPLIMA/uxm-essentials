package com.uxplima.uxmessentials.playerwarps.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The player-warps context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key
 * in {@code messages_<lang>.conf} ({@code PWARP_SET} ↔ {@code pwarp.set}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context —
 * every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum PlayerwarpsMessageKey implements MessageKey {

    // set / move / delete feedback
    PWARP_SET("pwarp.set"),
    PWARP_MOVED("pwarp.moved"),
    PWARP_DELETED("pwarp.deleted"),

    // visibility toggles
    PWARP_PUBLIC("pwarp.public"),
    PWARP_PRIVATE("pwarp.private"),

    // teleport
    PWARP_TELEPORTING("pwarp.teleporting"),

    // listing your own warps
    PWARP_LIST_HEADER("pwarp.list.header"),
    PWARP_LIST_ENTRY("pwarp.list.entry"),
    PWARP_LIST_EMPTY("pwarp.list.empty"),

    // listing another player's public warps
    PWARP_LIST_OTHER_HEADER("pwarp.list.other-header"),
    PWARP_LIST_OTHER_ENTRY("pwarp.list.other-entry"),
    PWARP_LIST_OTHER_EMPTY("pwarp.list.other-empty"),

    // failures
    PWARP_NOT_FOUND("pwarp.not-found"),
    PWARP_NAME_TAKEN("pwarp.name-taken"),
    PWARP_LIMIT_REACHED("pwarp.limit-reached"),
    PWARP_NOT_PUBLIC("pwarp.not-public"),
    PWARP_NONE("pwarp.none"),
    PWARP_UNSAFE("pwarp.unsafe"),
    PWARP_LOCKED("pwarp.locked"),
    PWARP_WRONG_PASSWORD("pwarp.wrong-password"),
    PWARP_PASSWORD_SET("pwarp.password-set"),
    PWARP_PASSWORD_CLEARED("pwarp.password-cleared"),
    PWARP_LOCK_TOGGLED("pwarp.lock-toggled"),
    PWARP_WORLD_BLACKLISTED("pwarp.world-blacklisted"),
    PWARP_RATED("pwarp.rated"),
    PWARP_RATING("pwarp.rating"),
    PWARP_RATING_INVALID("pwarp.rating-invalid");

    private final String key;

    PlayerwarpsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
