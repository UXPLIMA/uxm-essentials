package com.uxplima.uxmessentials.custommenus.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The custommenus context's user-visible message keys for the {@code /menu} command. Each constant maps 1:1 to a
 * kebab-case catalog key in {@code messages_<lang>.conf} ({@code MENU_NOT_FOUND} ↔ {@code menu.not-found}); the
 * constant is the compile-time handle, the catalog holds the text. There are no inline player-facing literals in
 * the context — every line the command shows resolves through one of these (the players-only rejection a console
 * meets reuses the shared {@code command.players-only} key).
 *
 * <p>Per the i18n contract a disabled module still ships its keys, so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set whether or not custommenus is enabled.
 */
public enum CustomMenusMessageKey implements MessageKey {

    /** Reply when {@code /menu open <name>} names a menu no loaded spec is registered under. */
    MENU_NOT_FOUND("menu.not-found"),

    /** Header line for {@code /menu list}. */
    MENU_LIST_HEADER("menu.list.header"),

    /** One registered menu name in the {@code /menu list} output ({@code {name}}). */
    MENU_LIST_ENTRY("menu.list.entry"),

    /** Reply for {@code /menu list} when no operator menus are registered. */
    MENU_LIST_EMPTY("menu.list.empty"),

    /** Reply for {@code /menu reload} reporting how many specs loaded and how many were skipped. */
    MENU_RELOADED("menu.reloaded"),

    /** Reply when a console (or other non-player) invokes a menu open command whose block forbids the console. */
    MENU_CONSOLE_DENIED("menu.console-denied"),

    /** Confirmation to the sender of {@code /menu open <name> <target>} that the menu opened for {@code {player}}. */
    MENU_OPENED_FOR("menu.opened-for");

    private final String key;

    CustomMenusMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
