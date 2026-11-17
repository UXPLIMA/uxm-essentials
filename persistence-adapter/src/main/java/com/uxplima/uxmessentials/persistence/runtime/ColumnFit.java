package com.uxplima.uxmessentials.persistence.runtime;

import java.util.Objects;
import java.util.regex.Pattern;

import org.jooq.Field;
import org.jspecify.annotations.NullMarked;

/**
 * A name cut to the length its column declares, and a player's name kept out of a column when it is only their uuid.
 *
 * <p>The name columns are sixty-four characters: a Floodgate name can be longer than a Java one, and an action taken
 * through the developer API is attributed to {@code <plugin>/<caller>}. MySQL and PostgreSQL refuse a longer value
 * rather than store it, which rolled back the whole write; SQLite ignores the length and kept it. Cutting it here keeps
 * the row, and keeps every backend storing the same thing. The full name is still in the audit line.
 *
 * <p>A player nobody has a name for travels with their uuid standing in as the name. Stored, it would read as a name
 * the player has, so {@link #name} leaves such a value out: the readers fall back to the uuid for a blank name.
 */
@NullMarked
public final class ColumnFit {

    /** A uuid written out, the stand-in a nameless player carries. */
    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ColumnFit() {}

    /** {@code value}, shortened to the declared length of {@code column} when it has one and the value is longer. */
    public static String fit(Field<String> column, String value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        int length = column.getDataType().length();
        return length > 0 && value.length() > length ? value.substring(0, length) : value;
    }

    /** {@code name} fitted to {@code column}, or blank when it is a uuid standing in for a name nobody knows. */
    public static String name(Field<String> column, String name) {
        Objects.requireNonNull(name, "name");
        return isUuid(name) ? "" : fit(column, name.strip());
    }

    /** Whether {@code name} is a uuid written out rather than a name. */
    public static boolean isUuid(String name) {
        return UUID_TEXT.matcher(Objects.requireNonNull(name, "name").strip()).matches();
    }
}
