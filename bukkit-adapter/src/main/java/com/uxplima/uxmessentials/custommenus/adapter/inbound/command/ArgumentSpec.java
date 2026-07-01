package com.uxplima.uxmessentials.custommenus.adapter.inbound.command;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One typed positional argument of a menu's {@code command {}} block: the {@code name} the parsed value is keyed
 * under (reachable in the opened menu as {@code %argument_<name>%}) and the {@link ArgType} that decides the
 * Brigadier node used to read and autocomplete it. A {@code /gift <target> <amount>} command declares two of these
 * — {@code {name="target", type="online-player"}} and {@code {name="amount", type="int"}} — in order.
 *
 * <p>Immutable and validated at construction: {@code name} must be non-blank (it becomes both a Brigadier argument
 * name and a placeholder key). An unrecognised type string is not this record's concern — the loader maps a config
 * token to an {@link ArgType} via {@link ArgType#parse(String)} and falls back to {@link ArgType#STRING} with a
 * warning when it does not recognise it, so a typo in the config degrades to a plain word argument rather than
 * dropping the command.
 */
@NullMarked
public record ArgumentSpec(String name, ArgType type) {

    public ArgumentSpec {
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isBlank()) {
            throw new IllegalArgumentException("menu command argument name must not be blank");
        }
        Objects.requireNonNull(type, "type");
    }

    /**
     * The kind of value an argument accepts, which fixes the Brigadier argument type and its autocomplete. The
     * numeric and boolean kinds parse to their native Brigadier types (so a non-number is rejected as a syntax
     * error before the menu opens); {@code ONLINE_PLAYER} resolves against the online roster; the remaining kinds
     * read a single word, some with a suggestion list ({@code PLAYER} online names, {@code MATERIAL} material
     * names, {@code WORLD} loaded world names).
     */
    public enum ArgType {
        STRING,
        INT,
        DOUBLE,
        BOOL,
        MATERIAL,
        WORLD,
        ONLINE_PLAYER,
        PLAYER;

        /**
         * Map a config type token to its {@link ArgType}, or empty when the token is not a recognised type. The
         * token is matched case-insensitively with {@code _} and spaces normalised to {@code -}, so
         * {@code online-player}, {@code online_player} and {@code ONLINE PLAYER} all name {@link #ONLINE_PLAYER}.
         * The loader turns an empty result into {@link #STRING} plus a warning, so this stays a pure lookup.
         */
        public static Optional<ArgType> parse(@Nullable String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String key = raw.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
            return switch (key) {
                case "string", "word", "text" -> Optional.of(STRING);
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
    }
}
