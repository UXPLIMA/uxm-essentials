package com.uxplima.uxmessentials.homes.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A home's name, normalised to its canonical lowercase form so uniqueness and lookup are
 * case-insensitive — {@code /sethome Base} and {@code /home base} address the same home.
 *
 * <p>The name is the home's identity within an owner's {@link HomeSet}; the raw form the player typed is
 * not retained, because two casings of one word are the same home and storing both would let the
 * uniqueness invariant be bypassed. Blank names and names longer than the column width are rejected at
 * construction, so an invalid name can never reach the aggregate or the repository.
 *
 * @param value the canonical lowercase name
 */
public record HomeName(String value) {

    /** Mirrors the {@code homes.name} column width in the V1 migration. */
    public static final int MAX_LENGTH = 64;

    public HomeName {
        Objects.requireNonNull(value, "value");
    }

    /** Build a name from raw player input, trimming and lower-casing it; rejects blank or overlong input. */
    public static HomeName of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalised = raw.strip().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("home name must not be blank");
        }
        if (normalised.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("home name must be at most " + MAX_LENGTH + " characters");
        }
        return new HomeName(normalised);
    }

    @Override
    public String toString() {
        return value;
    }
}
