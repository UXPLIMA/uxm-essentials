package com.uxplima.uxmessentials.regions.domain;

/**
 * The portable "shape" of a WorldGuard flag, decoupled from {@code com.sk89q}'s typed {@code Flag} registry: enough
 * for the flag editor to pick a type-appropriate control (cycle, toggle, text input, number input, choice picker, or
 * read-only) without the core ever naming a WorldGuard type. The adapter classifies each registered flag into one of
 * these kinds when it reads the registry, and converts the portable value the editor produces back to the flag's
 * concrete WorldGuard value on write. Pure Java: no Bukkit, Paper, Kyori, or WorldGuard.
 *
 * <ul>
 *   <li>{@link #STATE} a WorldGuard {@code StateFlag} (allow / deny / unset), cycled.
 *   <li>{@link #BOOLEAN} a {@code BooleanFlag} (true / false / unset), toggled.
 *   <li>{@link #STRING} a free-text flag (greeting, farewell, ...), edited through a text prompt.
 *   <li>{@link #INTEGER} a whole-number flag (heal-amount, ...), edited through a validated number prompt.
 *   <li>{@link #DOUBLE} a decimal flag (heal-min-health, ...), edited through a validated number prompt.
 *   <li>{@link #ENUM} a fixed-choice flag (game-mode, weather-lock, a region-group), picked from its choices.
 *   <li>{@link #OTHER} an unsupported-complex flag (a set, a location, ...): shown read-only, never edited here.
 * </ul>
 */
public enum FlagKind {

    /** A tri-state flag: allow / deny / unset, cycled in place. */
    STATE,

    /** A boolean flag: true / false / unset, toggled in place. */
    BOOLEAN,

    /** A free-text flag edited through a text prompt. */
    STRING,

    /** A whole-number flag edited through a validated number prompt. */
    INTEGER,

    /** A decimal flag edited through a validated number prompt. */
    DOUBLE,

    /** A fixed-choice flag picked from its {@code choices}. */
    ENUM,

    /** An unsupported-complex flag shown read-only. */
    OTHER;

    /** Whether the editor can change a flag of this kind; only {@link #OTHER} is read-only. */
    public boolean editable() {
        return this != OTHER;
    }
}
