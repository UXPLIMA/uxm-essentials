package com.uxplima.uxmessentials.homes.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The homes context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code HOME_SET} ↔ {@code home.set}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum HomesMessageKey implements MessageKey {

    // set / move / rename / delete feedback
    HOME_SET("home.set"),
    HOME_MOVED("home.moved"),
    HOME_RENAMED("home.renamed"),
    HOME_DELETED("home.deleted"),

    // teleport
    HOME_TELEPORTING("home.teleporting"),

    // listing
    HOME_LIST_HEADER("home.list.header"),
    HOME_LIST_ENTRY("home.list.entry"),
    HOME_LIST_EMPTY("home.list.empty"),

    // failures
    HOME_NOT_FOUND("home.not-found"),
    HOME_NONE("home.none"),
    HOME_NAME_TAKEN("home.name-taken"),
    HOME_LIMIT_REACHED("home.limit-reached"),

    // admin
    HOME_ADMIN_DELETED("home.admin.deleted"),
    HOME_ADMIN_LIST_HEADER("home.admin.list-header"),
    HOME_ADMIN_TARGET_UNKNOWN("home.admin.target-unknown");

    private final String key;

    HomesMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
