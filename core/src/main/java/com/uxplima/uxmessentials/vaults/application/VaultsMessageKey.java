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

    // /vault <player> [n] (admin)
    VAULT_ADMIN_OPENED("vaults.admin.opened"),
    VAULT_ADMIN_UNKNOWN_TARGET("vaults.admin.unknown-target"),

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
