package com.uxplima.uxmessentials.guard.domain;

import java.util.Objects;

/**
 * A region's identity on this server: its world and its id. Two worlds may each have a {@code spawn}.
 *
 * @param world the world name
 * @param id the region id
 */
public record RegionKey(String world, RegionId id) {

    public RegionKey {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        if (world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
    }

    @Override
    public String toString() {
        return world + ":" + id;
    }
}
