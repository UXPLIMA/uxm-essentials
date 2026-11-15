package com.uxplima.uxmessentials.vanish.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A player's vanish state as last written, read by the next backend they join. The level is the tier they were hidden
 * at; their next join re-derives it from their permissions, so the kept level only decides how hidden they are until
 * that runs.
 *
 * <p>A vanish ends when the player leaves the network, as it always has, unless they may persist it. What the row adds
 * is the server switch: a vanished player who left one backend a moment ago is on their way to another through the
 * proxy, and {@link #restores} keeps them hidden there.
 *
 * @param vanished whether the player is vanished
 * @param level the tier they were hidden at; always empty when they are not vanished
 * @param updatedAt when the vanish or reveal was written; a write older than the kept one loses
 * @param departedAt when the vanished player last left a backend; empty while they have not, and always empty when
 *     they are not vanished
 */
public record StoredVanish(
        boolean vanished, Optional<VanishLevel> level, Instant updatedAt, Optional<Instant> departedAt) {

    public StoredVanish {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(departedAt, "departedAt");
        if (!vanished && (level.isPresent() || departedAt.isPresent())) {
            throw new IllegalArgumentException("a visible player has no vanish level or departure");
        }
    }

    /** The state of a player vanished at {@code level}, written at {@code at}, still on the backend that wrote it. */
    public static StoredVanish vanished(VanishLevel level, Instant at) {
        Objects.requireNonNull(level, "level");
        return new StoredVanish(true, Optional.of(level), at, Optional.empty());
    }

    /** The state of a player who is not vanished, written at {@code at}. */
    public static StoredVanish visible(Instant at) {
        return new StoredVanish(false, Optional.empty(), at, Optional.empty());
    }

    /** This vanished state with the player having left a backend at {@code at}. */
    public StoredVanish departed(Instant at) {
        Objects.requireNonNull(at, "at");
        if (!vanished) {
            throw new IllegalStateException("only a vanished player's departure is kept");
        }
        return new StoredVanish(true, level, updatedAt, Optional.of(at));
    }

    /** The tier to hide the player at, the default tier when the row kept none. */
    public VanishLevel levelOrDefault() {
        return level.orElse(VanishLevel.DEFAULT);
    }

    /**
     * Whether a player joining at {@code now} comes back hidden from this state: they were vanished and either may
     * persist their vanish ({@code persist}), whatever its age, or left a backend no longer than {@code hopWindow} ago,
     * which is a server switch rather than a return to the network.
     */
    public boolean restores(boolean persist, Instant now, Duration hopWindow) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(hopWindow, "hopWindow");
        if (!vanished) {
            return false;
        }
        return persist
                || departedAt.map(left -> !now.isAfter(left.plus(hopWindow))).orElse(false);
    }
}
