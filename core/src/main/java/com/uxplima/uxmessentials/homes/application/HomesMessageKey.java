package com.uxplima.uxmessentials.homes.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The homes context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code HOME_CREATED} ↔ {@code home.created}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum HomesMessageKey implements MessageKey {

    // create / relocate / relabel / re-icon / delete feedback
    HOME_CREATED("home.created"),
    HOME_DELETED("home.deleted"),
    HOME_RELOCATED("home.relocated"),
    HOME_RENAMED("home.renamed"),
    HOME_ICON_CHANGED("home.icon-changed"),

    // teleport
    HOME_TELEPORTING("home.teleporting"),

    // failures
    HOME_LIMIT_REACHED("home.limit-reached"),
    HOME_NOT_FOUND("home.not-found"),
    HOME_SLOT_TAKEN("home.slot-taken"),
    HOME_SLOT_OUT_OF_RANGE("home.slot-out-of-range"),

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
