package com.uxplima.uxmessentials.persistence.runtime;

import java.util.Objects;

import org.jooq.Field;
import org.jspecify.annotations.NullMarked;

/**
 * A name cut to the length its column declares.
 *
 * <p>The issuer and sender columns are sixteen characters, a player's name. An action taken through the developer
 * API is attributed to {@code <plugin>/<caller>}, which is longer, and MySQL and PostgreSQL refuse the row rather than
 * store it: a mute issued from a web panel was rolled back whole. SQLite ignores the length, so the same write kept
 * the whole name there. Cutting it here keeps the row, and keeps every backend storing the same thing. The full name
 * is still in the audit line.
 */
@NullMarked
public final class ColumnFit {

    private ColumnFit() {}

    /** {@code value}, shortened to the declared length of {@code column} when it has one and the value is longer. */
    public static String fit(Field<String> column, String value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        int length = column.getDataType().length();
        return length > 0 && value.length() > length ? value.substring(0, length) : value;
    }
}
