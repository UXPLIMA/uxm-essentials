package com.uxplima.uxmessentials.vaults.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The vaults context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code VAULT_OPENED} ↔ {@code vaults.opened}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum VaultsMessageKey implements MessageKey {

    // GUI window titles (rendered into a Component for the inventory view)
    VAULT_TITLE("vaults.title"),
    VAULT_ADMIN_TITLE("vaults.admin.title"),

    // /vault, /vault <n>
    VAULT_OPENED("vaults.opened"),
    VAULT_LIST_HEADER("vaults.list.header"),
    VAULT_LIST_ENTRY("vaults.list.entry"),
    VAULT_NONE_OWNED("vaults.none-owned"),
    VAULT_AMOUNT_EXCEEDED("vaults.amount-exceeded"),

    // /vault info
    VAULT_INFO_HEADER("vaults.info.header"),
    VAULT_INFO_LINE("vaults.info.line"),

    // /vault <player> [n] (admin)
    VAULT_ADMIN_OPENED("vaults.admin.opened"),
    VAULT_ADMIN_UNKNOWN_TARGET("vaults.admin.unknown-target"),

    // /vault delete <n>, /vault delete <player> <n>
    VAULT_DELETED("vaults.deleted"),
    VAULT_DELETE_UNKNOWN("vaults.delete-unknown"),
    VAULT_CANNOT_AFFORD("vaults.cannot-afford"),

    // item blacklist (items refused on save and returned to the player)
    VAULT_ITEM_BLOCKED("vaults.item-blocked"),

    // vault-selector GUI (/vault with several owned): one icon per index, owned vs locked
    VAULT_SELECTOR_TITLE("vaults.selector.title"),
    VAULT_SELECTOR_ENTRY_NAME("vaults.selector.entry.name"),
    VAULT_SELECTOR_ENTRY_LORE("vaults.selector.entry.lore"),
    VAULT_SELECTOR_LOCKED_NAME("vaults.selector.locked.name"),
    VAULT_SELECTOR_LOCKED_LORE("vaults.selector.locked.lore"),
    VAULT_SELECTOR_LOCKED_CLICK("vaults.selector.locked.click"),
    VAULT_SELECTOR_PREV("vaults.selector.prev"),
    VAULT_SELECTOR_NEXT("vaults.selector.next"),

    // shared
    VAULT_SAVED("vaults.saved"),
    VAULT_PLAYERS_ONLY("vaults.players-only");

    private final String key;

    VaultsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
