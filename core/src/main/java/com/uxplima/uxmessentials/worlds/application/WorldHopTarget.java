package com.uxplima.uxmessentials.worlds.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * What a world hop's stored target reference says: the world, and whether a staff member sent the player there. A
 * staff send skips the world's access rules on landing as a local {@code /worlds tp} does, and that has to survive the
 * trip to the other backend, so it rides in the reference. A world name can never hold a colon, so the prefix cannot
 * be mistaken for part of one.
 *
 * @param world the world the hop lands in
 * @param staff true for a staff {@code /worlds tp}, false for a player's own {@code /worlds spawn}
 */
record WorldHopTarget(WorldName world, boolean staff) {

    private static final String STAFF_PREFIX = "staff:";

    WorldHopTarget {
        Objects.requireNonNull(world, "world");
    }

    String encode() {
        return staff ? STAFF_PREFIX + world.value() : world.value();
    }

    /** The target {@code ref} names, or empty when it names no world this release can read. */
    static Optional<WorldHopTarget> decode(String ref) {
        Objects.requireNonNull(ref, "ref");
        boolean staff = ref.startsWith(STAFF_PREFIX);
        String name = staff ? ref.substring(STAFF_PREFIX.length()) : ref;
        try {
            return Optional.of(new WorldHopTarget(WorldName.of(name), staff));
        } catch (IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }
}
