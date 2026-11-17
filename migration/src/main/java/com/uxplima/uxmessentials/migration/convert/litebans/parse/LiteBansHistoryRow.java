package com.uxplima.uxmessentials.migration.convert.litebans.parse;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One {@code litebans_history} row: an account logging in under a name, which LiteBans records on every login so it
 * can name a punished player. Source-shaped: the uuid and the name as stored, unchecked.
 *
 * @param uuid the account's uuid as written
 * @param name the name it logged in under, or null when the row has none
 * @param date when it logged in under that name; {@link Instant#EPOCH} when the row has no date
 */
@NullMarked
public record LiteBansHistoryRow(String uuid, @Nullable String name, Instant date) {

    public LiteBansHistoryRow {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(date, "date");
    }
}
