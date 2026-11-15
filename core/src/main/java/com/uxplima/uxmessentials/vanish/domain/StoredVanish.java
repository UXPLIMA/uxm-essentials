package com.uxplima.uxmessentials.vanish.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A player's vanish state as kept between sessions, so it follows them to another backend and through a restart. The
 * level is the tier they were hidden at when the state was written; their next join re-derives it from their
 * permissions, so the kept level only decides how hidden they are until that runs.
 *
 * @param vanished whether the player is vanished
 * @param level the tier they were hidden at; always empty when they are not vanished
 * @param updatedAt when the state was written; a write older than the kept one loses
 */
public record StoredVanish(boolean vanished, Optional<VanishLevel> level, Instant updatedAt) {

    public StoredVanish {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!vanished && level.isPresent()) {
            throw new IllegalArgumentException("a visible player has no vanish level: " + level.get());
        }
    }

    /** The state of a player vanished at {@code level}, written at {@code at}. */
    public static StoredVanish vanished(VanishLevel level, Instant at) {
        Objects.requireNonNull(level, "level");
        return new StoredVanish(true, Optional.of(level), at);
    }

    /** The state of a player who is not vanished, written at {@code at}. */
    public static StoredVanish visible(Instant at) {
        return new StoredVanish(false, Optional.empty(), at);
    }

    /** The tier to hide the player at, the default tier when the row kept none. */
    public VanishLevel levelOrDefault() {
        return level.orElse(VanishLevel.DEFAULT);
    }
}
