package com.uxplima.uxmessentials.discordlink.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Discord user's snowflake id, carried as text. A snowflake is a 64-bit integer Discord renders as a
 * decimal string of roughly 17-20 digits; the bridge receives it as a string from the slash-command event and
 * never does arithmetic on it, so it stays a string here rather than a {@code long} (which would risk an
 * overflow surprise and lose a leading-zero edge the API never emits but the type would).
 *
 * <p>Construction rejects a blank or non-numeric value so a malformed id can never reach the store; the value
 * is the binding's other half, unique per confirmed link.
 *
 * @param value the decimal snowflake string
 */
public record DiscordId(String value) {

    private static final Pattern DIGITS = Pattern.compile("^[0-9]{15,20}$");

    public DiscordId {
        Objects.requireNonNull(value, "value");
        if (!DIGITS.matcher(value).matches()) {
            throw new IllegalArgumentException("discord id must be 15-20 digits: " + value);
        }
    }

    /** Build an id from raw input, stripping surrounding whitespace; rejects a blank or non-numeric value. */
    public static DiscordId of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new DiscordId(raw.strip());
    }

    @Override
    public String toString() {
        return value;
    }
}
