package com.uxplima.uxmessentials.shared.application.message;

/**
 * The shared kernel's common message keys — the cross-cutting block no single feature context owns.
 *
 * <p>Three families live here (docs/13-i18n §6, the {@code shared} row): the {@code command.*} failures
 * every command-bearing context raises before it reaches a use case (no permission, player-only,
 * unknown player), the {@code cooldown.*} / {@code warmup.*} feedback the shared {@code Cooldowns} /
 * {@code Warmups} ports render, and the {@code lang.*} feedback the {@code /lang} override command
 * emits. They sit in {@code shared} so any context references them without a cross-context dependency,
 * exactly as a per-feature {@code MessageKey} enum does for its own block.
 *
 * <p>Like every {@link MessageKey} enum the constant name and the catalog key map 1:1
 * ({@code COMMAND_NO_PERMISSION} ↔ {@code command.no-permission}); the locale-parity guard asserts each
 * has an {@code en} entry and that the {@code command.*} / {@code cooldown.*} / {@code warmup.*} /
 * {@code lang.*} namespaces are owned here.
 */
public enum SharedMessageKey implements MessageKey {

    // cross-cutting command failures shared by every command-bearing context
    COMMAND_NO_PERMISSION("command.no-permission"),
    COMMAND_PLAYERS_ONLY("command.players-only"),
    COMMAND_UNKNOWN_PLAYER("command.unknown-player"),

    // shared cooldown / warmup feedback rendered from the Cooldowns / Warmups ports
    COOLDOWN_ACTIVE("cooldown.active"),
    WARMUP_STARTED("warmup.started"),
    WARMUP_CANCELLED("warmup.cancelled"),

    // the /lang personal locale override command
    LANG_STATUS("lang.status"),
    LANG_SET("lang.set"),
    LANG_RESET("lang.reset"),
    LANG_UNKNOWN("lang.unknown"),
    LANG_AVAILABLE("lang.available");

    private final String key;

    SharedMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
