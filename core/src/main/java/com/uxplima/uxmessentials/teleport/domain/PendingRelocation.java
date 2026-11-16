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
        UUID actor) {

    public PendingRelocation {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(actor, "actor");
        createdAt = Instant.ofEpochMilli(createdAt.toEpochMilli());
    }
}
