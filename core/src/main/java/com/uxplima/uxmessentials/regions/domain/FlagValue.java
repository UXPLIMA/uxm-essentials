package com.uxplima.uxmessentials.regions.domain;

import java.util.Objects;

/**
 * One region flag as a plain, rendered name/value pair — the domain view of a WorldGuard flag decoupled from its
 * {@code com.sk89q} {@code Flag} type. The adapter maps a live flag entry to this ({@code pvp = DENY},
 * {@code greeting = "Welcome"}), so the application and the (Phase 2) flag editor reason over strings rather than
 * WorldGuard's typed flag registry.
 *
 * <p>{@link #value()} is the flag's current value stringified, or an empty string when the flag is set but carries
 * no printable value. Pure Java: no Bukkit, Paper, Kyori, or WorldGuard.
 *
 * @param name the flag's registered name (e.g. {@code pvp}, {@code greeting})
 * @param value the flag's current value rendered as a string, or empty when it has none
 */
public record FlagValue(String name, String value) {

    public FlagValue {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank()) {
            throw new IllegalArgumentException("flag name must not be blank");
        }
    }
}
