package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.Optional;

/**
 * A world the plugin manages.
 *
 * <p>Worlds the operator never took under management are not here, even when the server has them loaded: this is
 * the plugin's register, not Bukkit's. The {@code loaded} flag and the player count are read at the moment of the
 * call, so a world that is known but currently unloaded still describes itself fully.
 *
 * @param name the folder name, which is what commands take
 * @param alias the display name the operator gave it, or empty when they never gave one
 * @param environment {@code NORMAL}, {@code NETHER} or {@code THE_END}
 * @param generation the generation preset it was created with, for example {@code NORMAL}, {@code FLAT} or
 *     {@code VOID}
 * @param seed the seed it was created with, or empty when the server chose one
 * @param autoLoad whether the plugin loads it on startup
 * @param loaded whether it is loaded right now
 * @param playerCount how many players are in it right now, zero when it is not loaded
 */
public record UxmWorld(
        String name,
        Optional<String> alias,
        String environment,
        String generation,
        Optional<Long> seed,
        boolean autoLoad,
        boolean loaded,
        int playerCount) {

    public UxmWorld {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(seed, "seed");
        if (playerCount < 0) {
            throw new IllegalArgumentException("playerCount must not be negative: " + playerCount);
        }
    }

    /** The alias if there is one, otherwise the folder name, which is what the plugin itself displays. */
    public String displayName() {
        return alias.orElse(name);
    }
}
