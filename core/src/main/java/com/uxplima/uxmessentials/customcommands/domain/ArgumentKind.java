package com.uxplima.uxmessentials.customcommands.domain;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The kind of value one declared argument accepts. The domain knows the kinds so it can validate a declaration
 * (bounds belong to a number, a rest capture belongs to text); it deliberately knows nothing about how each kind
 * is parsed, which is the adapter's job when it builds the Brigadier node.
 */
public enum ArgumentKind {
    STRING,
    INT,
    DOUBLE,
    BOOL,
    MATERIAL,
    WORLD,
    ONLINE_PLAYER,
    PLAYER;

    /**
     * Map a config token to its kind, or empty when the token names none. The token is matched case-insensitively
     * with underscores and spaces normalised to dashes, so {@code online-player}, {@code online_player} and
     * {@code ONLINE PLAYER} all name {@link #ONLINE_PLAYER}. {@code rest} names {@link #STRING}: a rest capture is
     * a string that takes the remaining input, and the loader records that in the argument's own flag.
     */
    public static Optional<ArgumentKind> parse(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        return switch (key) {
            case "string", "word", "text" -> Optional.of(STRING);
            case "rest", "greedy", "remainder" -> Optional.of(STRING);
            case "int", "integer" -> Optional.of(INT);
            case "double", "decimal", "number" -> Optional.of(DOUBLE);
            case "bool", "boolean" -> Optional.of(BOOL);
            case "material", "item" -> Optional.of(MATERIAL);
            case "world" -> Optional.of(WORLD);
            case "online-player", "player-online" -> Optional.of(ONLINE_PLAYER);
            case "player", "offline-player", "any-player" -> Optional.of(PLAYER);
            default -> Optional.empty();
        };
    }

    /** Whether a config token asks for the rest of the input rather than a single word. */
    public static boolean isRestToken(@Nullable String raw) {
        if (raw == null) {
            return false;
        }
        String key = raw.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        return key.equals("rest") || key.equals("greedy") || key.equals("remainder");
    }

    /** Whether a numeric range may be declared for this kind. */
    public boolean numeric() {
        return this == INT || this == DOUBLE;
    }

    /** The config token this kind is written as, so a writer round-trips what a loader read. */
    public String token() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
