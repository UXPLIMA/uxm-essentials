package com.uxplima.uxmessentials.villagers.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The villagers context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code VILLAGERS_TRADES_DISABLED} ↔ {@code villagers.trades-disabled}); the constant
 * is the compile-time handle, the catalog holds the text. There are no inline player-facing literals in the context —
 * every message resolves through one of these.
 *
 * <p>Phase 1 seeded the one refusal a player sees when they right-click a villager whose trading is turned off
 * (globally or per-villager); Phase 2 adds the trade-manager surface — the "look at a villager" hint, the manager
 * GUI title, and the two button labels (the disable toggle, in its two states, and the per-row remove control) plus
 * the usage help. Later phases add their own keys here as their verbs land.
 */
public enum VillagersMessageKey implements MessageKey {

    // Refusal — the villager's trading is disabled (by the global switch or a per-villager flag), so the trade GUI
    // does not open.
    VILLAGERS_TRADES_DISABLED("villagers.trades-disabled"),

    // Trade manager — /villager manager could not find a villager the player is looking at (or within reach).
    VILLAGERS_MANAGER_NO_TARGET("villagers.manager.no-target"),

    // Trade manager — the GUI window title (carries the villager's {name}).
    VILLAGERS_MANAGER_TITLE("villagers.manager.title"),

    // Trade manager — the disable-toggle button when the villager IS trading (click disables it).
    VILLAGERS_MANAGER_TRADING_ENABLED("villagers.manager.trading-enabled"),

    // Trade manager — the disable-toggle button when the villager is NOT trading (click re-enables it).
    VILLAGERS_MANAGER_TRADING_DISABLED("villagers.manager.trading-disabled"),

    // Trade manager — the per-recipe remove button label.
    VILLAGERS_MANAGER_REMOVE("villagers.manager.remove"),

    // Trade manager — the info/help item explaining how to edit, add and remove trades.
    VILLAGERS_MANAGER_HELP("villagers.manager.help");

    private final String key;

    VillagersMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
