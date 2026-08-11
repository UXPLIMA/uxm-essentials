package com.uxplima.uxmessentials.api.view;

import java.util.List;
import java.util.Objects;

/**
 * A WorldGuard region as this plugin reads it.
 *
 * <p>The roster entries are identifiers rather than players, because that is what a region holds: a uuid, a name
 * recorded before uuids existed, or a group written as {@code g:name}. Resolving one to an account is the caller's
 * business, and not always possible.
 *
 * <p>The shape is deliberately not here. A region may be a cuboid, a polygon or the whole world, and a corner pair
 * would be a lie for two of the three. What is here is what a list, a lookup or a permission check needs.
 *
 * @param world the name of the world the region is defined in
 * @param id the region id, unique within that world
 * @param priority the region's priority, where higher wins an overlap
 * @param owners the owner identifiers
 * @param members the member identifiers
 * @param flags the flags the region sets, which is not every flag that exists
 */
public record UxmRegion(
        String world, String id, int priority, List<String> owners, List<String> members, List<UxmRegionFlag> flags) {

    public UxmRegion {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        owners = List.copyOf(owners);
        members = List.copyOf(members);
        flags = List.copyOf(flags);
    }

    /** The value of one flag by name, or empty when the region does not set it. */
    public java.util.Optional<String> flag(String name) {
        Objects.requireNonNull(name, "name");
        return flags.stream()
                .filter(flag -> flag.name().equals(name))
                .map(UxmRegionFlag::value)
                .findFirst();
    }
}
