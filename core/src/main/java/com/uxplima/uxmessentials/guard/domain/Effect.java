package com.uxplima.uxmessentials.guard.domain;

import java.util.Locale;
import java.util.Optional;

/** What a rule does to the action it names. */
public enum Effect {
    ALLOW,
    DENY;

    /** The lower case word used in commands and storage. */
    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parse {@code allow} or {@code deny}, ignoring case. */
    public static Optional<Effect> parse(String raw) {
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "allow", "izin" -> Optional.of(ALLOW);
            case "deny", "yasak" -> Optional.of(DENY);
            default -> Optional.empty();
        };
    }
}
