package com.uxplima.uxmessentials.survival.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The survival context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code SURVIVAL_TREEFELLER_ENABLED} ↔ {@code survival.treefeller-enabled}); the
 * constant is the compile-time handle, the catalog holds the text. There are no inline player-facing literals in the
 * context — the {@code /treefeller} and {@code /veinminer} toggles resolve their on/off notices through these.
 *
 * <p>Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the locale-parity
 * guard sees the full {@code en} key set. Later phases add their own keys here as their mechanics land.
 */
public enum SurvivalMessageKey implements MessageKey {

    // /treefeller — the per-player toggle's on and off notices.
    SURVIVAL_TREEFELLER_ENABLED("survival.treefeller-enabled"),
    SURVIVAL_TREEFELLER_DISABLED("survival.treefeller-disabled"),

    // /veinminer — the per-player toggle's on and off notices.
    SURVIVAL_VEINMINER_ENABLED("survival.veinminer-enabled"),
    SURVIVAL_VEINMINER_DISABLED("survival.veinminer-disabled"),

    // /farmprotect — the per-player toggle's on and off notices.
    SURVIVAL_FARMPROTECT_ENABLED("survival.farmprotect-enabled"),
    SURVIVAL_FARMPROTECT_DISABLED("survival.farmprotect-disabled");

    private final String key;

    SurvivalMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
