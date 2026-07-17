package com.uxplima.uxmessentials.vanish.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The vanish context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code VANISH_ON} ↔ {@code vanish.on}); the constant is the compile-time handle, the
 * catalog holds the text. There are no inline player-facing literals in the context — every message resolves through
 * one of these. Phase 1 owns only the {@code /vanish} on/off confirmations; later phases add fake join/quit and the
 * action-bar indicator here as their behaviour lands.
 */
public enum VanishMessageKey implements MessageKey {

    // /vanish — the on/off confirmation shown to the toggling player.
    VANISH_ON("vanish.on"),
    VANISH_OFF("vanish.off");

    private final String key;

    VanishMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
