package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The NBT tag type a raw string value maps to. Operators write raw-NBT as plain text (a HOCON/YAML scalar),
 * so the type is inferred from the text: integer text is an {@link #INT} tag, otherwise decimal text is a
 * {@link #DOUBLE} tag, otherwise it stays a {@link #STRING} tag. This is the pure, SDK-free decision the
 * reflective applier consults to pick {@code setInteger} / {@code setDouble} / {@code setString}; keeping it
 * here makes the classification unit-testable without NBT-API on the classpath.
 */
@NullMarked
enum NbtTagType {
    INT,
    DOUBLE,
    STRING;

    /** Classify {@code value} by parseability: integer first, then decimal, otherwise a plain string. */
    static NbtTagType infer(String value) {
        Objects.requireNonNull(value, "value");
        if (parsesAsInteger(value)) {
            return INT;
        }
        if (parsesAsDouble(value)) {
            return DOUBLE;
        }
        return STRING;
    }

    private static boolean parsesAsInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException notAnInteger) {
            return false;
        }
    }

    private static boolean parsesAsDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException notADouble) {
            return false;
        }
    }
}
