package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A player-warp's name, normalised to its canonical lowercase form so uniqueness and lookup are
 * case-insensitive — {@code /setpwarp Base} and {@code /pwarp base} address the same warp.
 *
 * <p>Unlike a server warp, a player-warp's name is unique only <em>per owner</em>: two players may each own a
 * warp named {@code base}. The name is therefore the per-owner identity segment, not a server-wide one, and a
 * player-warp carries no per-warp permission node (access is by ownership and the public flag). Blank names
 * and names longer than the column width are rejected at construction, so an invalid name can never reach the
 * aggregate or the repository.
 *
 * @param value the canonical lowercase name
 */
public record PlayerWarpName(String value) {

    /** Mirrors the {@code player_warps.name} column width in the V14 migration. */
    public static final int MAX_LENGTH = 64;

    public PlayerWarpName {
        Objects.requireNonNull(value, "value");
    }

    /** Build a name from raw player input, trimming and lower-casing it; rejects blank or overlong input. */
    public static PlayerWarpName of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalised = raw.strip().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("player warp name must not be blank");
        }
        if (normalised.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("player warp name must be at most " + MAX_LENGTH + " characters");
        }
        return new PlayerWarpName(normalised);
    }

    @Override
    public String toString() {
        return value;
    }
}
