package com.uxplima.uxmessentials.villagers.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The villagers context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code VILLAGERS_TRADES_DISABLED} ↔ {@code villagers.trades-disabled}); the constant
 * is the compile-time handle, the catalog holds the text. There are no inline player-facing literals in the context —
 * every message resolves through one of these.
 *
 * <p>This is the Phase-1 seed: the one refusal a player sees when they right-click a villager whose trading is turned
 * off (globally or per-villager). Later phases add their own keys here as their verbs land.
 */
public enum VillagersMessageKey implements MessageKey {

    // Refusal — the villager's trading is disabled (by the global switch or a per-villager flag), so the trade GUI
    // does not open.
    VILLAGERS_TRADES_DISABLED("villagers.trades-disabled");

    private final String key;

    VillagersMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
