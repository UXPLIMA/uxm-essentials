package com.uxplima.uxmessentials.shared.domain;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * The worlds a warp may not be placed in, from a module's {@code world-blacklist} key. One value is shared by every
 * path that puts a warp somewhere (create, move, the menus, an import), so the paths cannot disagree about a world.
 *
 * <p>Names are matched exactly, case included, the way the key has always been read: an operator who listed a world
 * under one spelling keeps the same result.
 *
 * @param names the world names a warp is refused in
 */
public record WorldBlacklist(Set<String> names) {

    private static final WorldBlacklist NONE = new WorldBlacklist(Set.of());

    public WorldBlacklist {
        names = Set.copyOf(Objects.requireNonNull(names, "names"));
    }

    /** The list read from config. */
    public static WorldBlacklist of(Collection<String> names) {
        return new WorldBlacklist(Set.copyOf(Objects.requireNonNull(names, "names")));
    }

    /** A list that refuses no world. */
    public static WorldBlacklist none() {
        return NONE;
    }

    /** True when a warp may not be placed in {@code world}. */
    public boolean bars(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return names.contains(world.name());
    }
}
