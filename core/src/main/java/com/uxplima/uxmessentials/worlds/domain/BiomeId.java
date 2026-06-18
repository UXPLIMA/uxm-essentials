package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A namespaced-key reference to a biome (e.g. {@code minecraft:plains} or a bare {@code plains}).
 * The namespace is optional; when omitted the canonical form defaults to {@code minecraft:}. The
 * adapter maps this to a Bukkit {@code Biome}; the domain only carries the validated string.
 */
public record BiomeId(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    private static final String DEFAULT_NAMESPACE = "minecraft:";

    public BiomeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("biome id must not be blank");
        }
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("biome id must match " + VALID.pattern() + ": " + value);
        }
    }

    public static BiomeId of(String value) {
        return new BiomeId(value);
    }

    /** The {@code minecraft:}-defaulted form: returns {@link #value()} as-is when it carries a namespace. */
    public String namespacedValue() {
        return value.indexOf(':') >= 0 ? value : DEFAULT_NAMESPACE + value;
    }
}
