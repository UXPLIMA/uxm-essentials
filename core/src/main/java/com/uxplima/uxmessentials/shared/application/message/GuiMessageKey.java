package com.uxplima.uxmessentials.shared.application.message;

/**
 * The cross-cutting management-GUI message keys — the {@code gui.*} block the central hub and any
 * shared GUI scaffolding owns, distinct from a feature context's own menu keys.
 *
 * <p>The {@code /uxmess gui} hub lists every module's registered management-GUI entry as a clickable
 * icon. Its title, the per-entry name and lore, the navigation labels, and the empty-state line all
 * resolve here rather than being inlined, so the hub honours the locale pipeline and the UI-style canon
 * exactly as every feature menu does. The hub is shared infrastructure owned by no single context, so
 * its keys sit in the shared kernel under the {@code gui} prefix.
 *
 * <p>Like every {@link MessageKey} enum the constant name and the catalog key map 1:1
 * ({@code HUB_TITLE} ↔ {@code gui.hub.title}); the locale-parity guard asserts each has an {@code en}
 * entry and the catalog aggregate enumerates this enum.
 */
public enum GuiMessageKey implements MessageKey {

    // the /uxmess gui management hub
    HUB_TITLE("gui.hub.title"),
    HUB_ENTRY_NAME("gui.hub.entry.name"),
    HUB_ENTRY_LORE("gui.hub.entry.lore"),
    HUB_EMPTY("gui.hub.empty"),
    HUB_PREV("gui.hub.prev"),
    HUB_NEXT("gui.hub.next");

    private final String key;

    GuiMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
