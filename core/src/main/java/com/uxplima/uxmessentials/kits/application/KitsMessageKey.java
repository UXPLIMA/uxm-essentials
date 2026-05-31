package com.uxplima.uxmessentials.kits.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The kits context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code KIT_CLAIMED} ↔ {@code kit.claimed}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context —
 * every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum KitsMessageKey implements MessageKey {

    // claim feedback
    KIT_CLAIMED("kit.claimed"),

    // listing
    KIT_LIST_HEADER("kit.list.header"),
    KIT_LIST_ENTRY("kit.list.entry"),
    KIT_LIST_EMPTY("kit.list.empty"),

    // preview (/showkit)
    KIT_PREVIEW_HEADER("kit.preview.header"),
    KIT_PREVIEW_ENTRY("kit.preview.entry"),

    // authoring (/createkit /delkit /kiteditor)
    KIT_CREATED("kit.created"),
    KIT_DELETED("kit.deleted"),
    KIT_EDIT_OPENED("kit.edit.opened"),

    // reset (/kitreset)
    KIT_RESET("kit.reset"),
    KIT_RESET_ALL("kit.reset-all"),

    // failures
    KIT_NOT_FOUND("kit.not-found"),
    KIT_NONE("kit.none"),
    KIT_ALREADY_EXISTS("kit.already-exists"),
    KIT_NO_PERMISSION("kit.no-permission"),
    KIT_ON_COOLDOWN("kit.on-cooldown"),
    KIT_ALREADY_CLAIMED("kit.already-claimed"),
    KIT_CANNOT_AFFORD("kit.cannot-afford"),
    KIT_INVENTORY_FULL("kit.inventory-full");

    private final String key;

    KitsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
