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
 * The backend holding a vanished player refreshes {@code seenAt} while they are on it, which is how a reader tells a
 * player still online there from one whose backend went down without writing a departure.
 *
 * @param vanished whether the player is vanished
 * @param level the tier they were hidden at; always empty when they are not vanished
 * @param server the backend the vanished player was last on; always empty when they are not vanished
 * @param updatedAt when the vanish or reveal was written; a write older than the kept one loses
 * @param seenAt when {@code server} last confirmed the vanished player was on it; always empty when they are not
 *     vanished
 * @param departedAt when the vanished player left {@code server}; empty while they are on it, and always empty when
 *     they are not vanished
 */
public record StoredVanish(
        boolean vanished,
        Optional<VanishLevel> level,
        Optional<String> server,
        Instant updatedAt,
        Optional<Instant> seenAt,
        Optional<Instant> departedAt) {

    public StoredVanish {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(seenAt, "seenAt");
        Objects.requireNonNull(departedAt, "departedAt");
        if (!vanished && (level.isPresent() || server.isPresent() || seenAt.isPresent() || departedAt.isPresent())) {
            throw new IllegalArgumentException("a visible player has no vanish level, server, sighting or departure");
        }
    }

    /** The state of a player vanished at {@code level} on {@code server}, written and seen there at {@code at}. */
    public static StoredVanish vanished(VanishLevel level, String server, Instant at) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(server, "server");
        return new StoredVanish(true, Optional.of(level), Optional.of(server), at, Optional.of(at), Optional.empty());
    }

    /** The state of a player who is not vanished, written at {@code at}. */
    public static StoredVanish visible(Instant at) {
        return new StoredVanish(false, Optional.empty(), Optional.empty(), at, Optional.empty(), Optional.empty());
    }

    /** This vanished state with the player having left their server at {@code at}. */
    public StoredVanish departed(Instant at) {
        Objects.requireNonNull(at, "at");
        if (!vanished) {
            throw new IllegalStateException("only a vanished player's departure is kept");
        }
        return new StoredVanish(true, level, server, updatedAt, seenAt, Optional.of(at));
    }

    /** This vanished state with the player confirmed on {@code onServer} at {@code at}, and no departure. */
    public StoredVanish seenOn(String onServer, Instant at) {
        Objects.requireNonNull(onServer, "onServer");
        Objects.requireNonNull(at, "at");
        if (!vanished) {
            throw new IllegalStateException("only a vanished player is seen on a server");
        }
        return new StoredVanish(true, level, Optional.of(onServer), updatedAt, Optional.of(at), Optional.empty());
    }

    /** The tier to hide the player at, the default tier when the row kept none. */
    public VanishLevel levelOrDefault() {
        return level.orElse(VanishLevel.DEFAULT);
    }

    /** Whether their server confirmed the vanished player no longer than {@code window} before {@code now}. */
    public boolean seenWithin(Instant now, Duration window) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(window, "window");
        return seenAt.map(seen -> !now.isAfter(seen.plus(window))).orElse(false);
    }

    /**
     * Whether a player joining at {@code now} comes back hidden from this state. They must have been vanished, and then
     * any of these holds:
     *
     * <ul>
     *   <li>{@code persist}: they may keep their vanish across leaving the network, whatever its age.
     *   <li>No departure is recorded and {@code switching} is true: the proxy keeps the old connection until the new
     *       login succeeds, so the origin has not written its departure yet, and the caller has confirmed the player is
     *       still on another backend.
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
