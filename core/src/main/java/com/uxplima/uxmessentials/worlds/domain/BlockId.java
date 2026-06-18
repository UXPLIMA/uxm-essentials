package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A namespaced-key reference to a block type (e.g. {@code minecraft:stone} or a bare {@code stone}).
 * The namespace is optional; when omitted the canonical form defaults to {@code minecraft:}. The
 * adapter maps this to a Bukkit {@code Material}; the domain only carries the validated string.
 */
public record BlockId(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    private static final String DEFAULT_NAMESPACE = "minecraft:";

    public BlockId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("block id must not be blank");
        }
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("block id must match " + VALID.pattern() + ": " + value);
        }
    }

    public static BlockId of(String value) {
        return new BlockId(value);
    }

    /** The {@code minecraft:}-defaulted form: returns {@link #value()} as-is when it carries a namespace. */
    public String namespacedValue() {
        return value.indexOf(':') >= 0 ? value : DEFAULT_NAMESPACE + value;
    }
}
