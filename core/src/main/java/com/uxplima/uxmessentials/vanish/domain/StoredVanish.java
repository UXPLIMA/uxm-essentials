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
 * <p>A vanish ends when the player leaves the network unless they may persist it. What the row adds is the server
 * switch: {@link #restores} tells a player on their way to another backend through the proxy from one coming back.
 *
 * @param vanished whether the player is vanished
 * @param level the tier they were hidden at; always empty when they are not vanished
 * @param server the backend the vanished player was last on; always empty when they are not vanished
 * @param updatedAt when the vanish or reveal was written; a write older than the kept one loses
 * @param departedAt when the vanished player left {@code server}; empty while they are on it, and always empty when
 *     they are not vanished
 */
public record StoredVanish(
        boolean vanished,
        Optional<VanishLevel> level,
        Optional<String> server,
        Instant updatedAt,
        Optional<Instant> departedAt) {

    public StoredVanish {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(departedAt, "departedAt");
        if (!vanished && (level.isPresent() || server.isPresent() || departedAt.isPresent())) {
            throw new IllegalArgumentException("a visible player has no vanish level, server or departure");
        }
    }

    /** The state of a player vanished at {@code level} on {@code server}, written at {@code at}. */
    public static StoredVanish vanished(VanishLevel level, String server, Instant at) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(server, "server");
        return new StoredVanish(true, Optional.of(level), Optional.of(server), at, Optional.empty());
    }

    /** The state of a player who is not vanished, written at {@code at}. */
    public static StoredVanish visible(Instant at) {
        return new StoredVanish(false, Optional.empty(), Optional.empty(), at, Optional.empty());
    }

    /** This vanished state with the player having left their server at {@code at}. */
    public StoredVanish departed(Instant at) {
        Objects.requireNonNull(at, "at");
        if (!vanished) {
            throw new IllegalStateException("only a vanished player's departure is kept");
        }
        return new StoredVanish(true, level, server, updatedAt, Optional.of(at));
    }

    /** The tier to hide the player at, the default tier when the row kept none. */
    public VanishLevel levelOrDefault() {
        return level.orElse(VanishLevel.DEFAULT);
    }

    /**
     * Whether a player joining at {@code now} comes back hidden from this state. They must have been vanished, and then
     * any of these holds:
     *
     * <ul>
     *   <li>{@code persist}: they may keep their vanish across leaving the network, whatever its age.
     *   <li>No departure is recorded and {@code switching} is true: the proxy keeps the old connection until the new
     *       login succeeds, so the origin has not written its departure yet, and the caller has confirmed the player is
     *       still online elsewhere or their server is still running.
     *   <li>They left their server no longer than {@code hopWindow} ago.
     * </ul>
     */
    public boolean restores(boolean persist, boolean switching, Instant now, Duration hopWindow) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(hopWindow, "hopWindow");
        if (!vanished) {
            return false;
        }
        if (persist) {
            return true;
        }
        return departedAt.map(left -> !now.isAfter(left.plus(hopWindow))).orElse(switching);
    }
}
