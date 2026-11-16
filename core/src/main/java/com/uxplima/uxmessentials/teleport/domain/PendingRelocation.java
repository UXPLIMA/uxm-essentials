package com.uxplima.uxmessentials.teleport.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Where an offline player is moved once they next join one backend, as {@code /tpofflinehere} left it. The player,
 * the backend, the instant it was set and the staff member who set it name one stored relocation, so applying it
 * removes exactly that one and never a later one that replaced it.
 *
 * @param player the player to move
 * @param server the backend the relocation applies on
 * @param world the uid of the world to land in
 * @param createdAt when it was set, kept to the millisecond
 * @param actor the staff member who set it
 * @param misses how many joins already tried it and missed, which bounds how long it is kept
 */
public record PendingRelocation(
        UUID player,
        String server,
        UUID world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        Instant createdAt,
        UUID actor,
        int misses) {

    public PendingRelocation {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(actor, "actor");
        if (misses < 0) {
            throw new IllegalArgumentException("misses must not be negative: " + misses);
        }
        createdAt = Instant.ofEpochMilli(createdAt.toEpochMilli());
    }

    /** The same relocation after one more join tried it and missed. */
    public PendingRelocation missedOnce() {
        return new PendingRelocation(player, server, world, x, y, z, yaw, pitch, createdAt, actor, misses + 1);
    }
}
