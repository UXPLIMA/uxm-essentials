package com.uxplima.uxmessentials.holograms.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The holograms context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code HOLOGRAM_CREATED} ↔ {@code hologram.created}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum HologramsMessageKey implements MessageKey {

    // create / delete / move feedback
    HOLOGRAM_CREATED("hologram.created"),
    HOLOGRAM_DELETED("hologram.deleted"),
    HOLOGRAM_MOVED("hologram.moved"),
    HOLOGRAM_TELEPORTED("hologram.teleported"),

    // line editing feedback
    HOLOGRAM_LINE_ADDED("hologram.line.added"),
    HOLOGRAM_LINE_SET("hologram.line.set"),
    HOLOGRAM_LINE_REMOVED("hologram.line.removed"),

    // listing
    HOLOGRAM_LIST_HEADER("hologram.list.header"),
    HOLOGRAM_LIST_ENTRY("hologram.list.entry"),
    HOLOGRAM_LIST_EMPTY("hologram.list.empty"),

    // failures
    HOLOGRAM_NOT_FOUND("hologram.not-found"),
    HOLOGRAM_NAME_TAKEN("hologram.name-taken"),
    HOLOGRAM_LINE_INDEX_INVALID("hologram.line-index-invalid"),
    HOLOGRAM_MIN_ONE_LINE("hologram.min-one-line"),
    HOLOGRAM_PLAYERS_ONLY("hologram.players-only");

    private final String key;

    HologramsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
