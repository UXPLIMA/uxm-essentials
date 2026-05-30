package com.uxplima.uxmessentials.warps.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The warps context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code WARP_SET} ↔ {@code warp.set}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum WarpsMessageKey implements MessageKey {

    // set / move / delete feedback
    WARP_SET("warp.set"),
    WARP_MOVED("warp.moved"),
    WARP_DELETED("warp.deleted"),

    // teleport
    WARP_TELEPORTING("warp.teleporting"),

    // listing
    WARP_LIST_HEADER("warp.list.header"),
    WARP_LIST_ENTRY("warp.list.entry"),
    WARP_LIST_EMPTY("warp.list.empty"),

    // info
    WARP_INFO_HEADER("warp.info.header"),
    WARP_INFO_OWNER("warp.info.owner"),
    WARP_INFO_CREATED("warp.info.created"),
    WARP_INFO_COST("warp.info.cost"),
    WARP_INFO_PERMISSION("warp.info.permission"),

    // failures
    WARP_NOT_FOUND("warp.not-found"),
    WARP_NONE("warp.none"),
    WARP_NAME_TAKEN("warp.name-taken"),
    WARP_NO_PERMISSION("warp.no-permission"),
    WARP_CANNOT_AFFORD("warp.cannot-afford");

    private final String key;

    WarpsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
