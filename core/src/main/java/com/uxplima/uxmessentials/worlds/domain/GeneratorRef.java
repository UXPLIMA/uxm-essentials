package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;

/**
 * Opaque reference to an external chunk generator in {@code plugin[:args]} form (e.g.
 * {@code VoidGen} or {@code Multiverse:flat}). The domain never interprets it; the adapter passes
 * it to {@code WorldCreator.generator(...)}. Sub-project C adds our own built-in generators.
 */
public record GeneratorRef(String value) {

    public GeneratorRef {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("generator ref must not be blank");
        }
    }

    public static GeneratorRef of(String value) {
        return new GeneratorRef(value);
    }
}
