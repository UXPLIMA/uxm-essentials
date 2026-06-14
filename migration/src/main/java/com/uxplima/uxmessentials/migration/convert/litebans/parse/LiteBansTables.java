package com.uxplima.uxmessentials.migration.convert.litebans.parse;

import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.NullMarked;

/**
 * Resolves the three LiteBans table names from the configured {@code table_prefix} (default
 * {@code litebans_}) and builds the {@code SELECT} statements the reader runs against them. A table name
 * cannot be a JDBC bind parameter, so the prefix is interpolated into the SQL text — which makes it the one
 * place a malicious or malformed prefix could become an injection. It is therefore <em>strictly</em>
 * whitelisted to {@code [a-z0-9_]+} before any SQL is built; anything else is rejected at construction, long
 * before a statement is prepared. Every value the reader binds afterwards travels as a bind parameter, never
 * concatenation.
 */
@NullMarked
public final class LiteBansTables {

    private static final Pattern SAFE_PREFIX = Pattern.compile("[a-z0-9_]+");

    private final String bans;
    private final String mutes;
    private final String warnings;

    /**
     * Build the table set for {@code tablePrefix}.
     *
     * @param tablePrefix the LiteBans table prefix, sanitised to {@code [a-z0-9_]+}
     * @throws IllegalArgumentException when the prefix contains anything outside the whitelist
     */
    public LiteBansTables(String tablePrefix) {
        Objects.requireNonNull(tablePrefix, "tablePrefix");
        String prefix = tablePrefix.strip();
        if (prefix.isEmpty() || !SAFE_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException(
                    "litebans table prefix must match [a-z0-9_]+ (a table name cannot be a bind parameter): "
                            + tablePrefix);
        }
        this.bans = prefix + "bans";
        this.mutes = prefix + "mutes";
        this.warnings = prefix + "warnings";
    }

    /** The {@code SELECT} over the bans table. The prefix is sanitised; the table name is not user data. */
    public String selectBans() {
        return select(bans);
    }

    /** The {@code SELECT} over the mutes table. */
    public String selectMutes() {
        return select(mutes);
    }

    /** The {@code SELECT} over the warnings table (adds the {@code warned} column to the projection). */
    public String selectWarnings() {
        return "SELECT uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, "
                + "ipban, ipban_wildcard, active, warned FROM " + warnings;
    }

    private static String select(String table) {
        return "SELECT uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, "
                + "ipban, ipban_wildcard, active FROM " + table;
    }
}
