package com.uxplima.uxmessentials.regions.domain;

import java.util.List;
import java.util.Objects;

/**
 * One registered WorldGuard flag described for the flag editor, decoupled from its {@code com.sk89q} {@code Flag}
 * type: the flag's registered {@link #name()}, its portable {@link #kind()}, the region's current {@link #value()}
 * rendered as a string, and (for a {@link FlagKind#ENUM} flag) the {@link #choices()} the editor may offer. The
 * adapter builds one of these per registered flag by reading WorldGuard's flag registry and the region's set values,
 * so the application and GUI reason over these plain values rather than WorldGuard's typed registry, and the editor
 * can render a type-appropriate control per {@link #kind()}.
 *
 * <p>{@link #value()} is the flag's current value in the region, stringified in a form the same seam round-trips back
 * on write ({@code "ALLOW"}/{@code "DENY"} for a state, {@code "true"}/{@code "false"} for a boolean, the number for a
 * numeric flag, the enum-constant name or registry id for a choice flag), or the empty string when the flag is not set
 * in the region (so it falls back to WorldGuard's default). {@link #choices()} is non-empty only for {@link FlagKind
 * #ENUM}. Pure Java: no Bukkit, Paper, Kyori, or WorldGuard.
 *
 * @param name the flag's registered name (e.g. {@code pvp}, {@code greeting}, {@code game-mode})
 * @param kind the portable shape the editor renders a control for
 * @param value the flag's current value rendered as a portable string, or empty when the flag is unset
 * @param choices the allowed values for an {@link FlagKind#ENUM} flag, in display order; empty for every other kind
 */
public record FlagDescriptor(String name, FlagKind kind, String value, List<String> choices) {

    public FlagDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
        if (name.isBlank()) {
            throw new IllegalArgumentException("flag name must not be blank");
        }
    }

    /** A descriptor with no choices, for a flag that is not a fixed-choice ({@link FlagKind#ENUM}) flag. */
    public static FlagDescriptor of(String name, FlagKind kind, String value) {
        return new FlagDescriptor(name, kind, value, List.of());
    }

    /** Whether the flag carries no value in the region (it falls back to WorldGuard's default). */
    public boolean unset() {
        return value.isBlank();
    }

    /** Whether the editor can change this flag; only an {@link FlagKind#OTHER} flag is read-only. */
    public boolean editable() {
        return kind.editable();
    }
}
