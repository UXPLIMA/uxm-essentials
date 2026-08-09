package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A server warp: the kind an operator sets with {@code /setwarp} and every player shares.
 *
 * <p>The cost is the figure a visit would charge, absent when the warp is free, and the required permission is the
 * node the operator attached, absent when anyone may use it. Neither is a decision: a consumer that wants to know
 * whether a particular player may warp there should ask {@link UxmWarpsQuery#visibleTo}, which applies the same
 * filter {@code /warps} does.
 *
 * @param name the warp's id, which is also what a player types
 * @param location where it points
 * @param ownerId the player who created it
 * @param ownerName the name that player had when it was created
 * @param createdAt when it was set
 * @param cost what a visit charges, or empty when the warp is free
 * @param requiredPermission the node a player needs, or empty when the warp is open to everyone
 * @param visitors how many visits it has recorded
 * @param locked whether the operator has closed it to visits
 * @param passwordProtected whether a password is set (the password itself is never published)
 * @param category the category id it was filed under, or empty
 * @param icon the material id of its menu icon, or empty for the default
 */
public record UxmWarp(
        String name,
        UxmLocation location,
        UUID ownerId,
        String ownerName,
        Instant createdAt,
        Optional<UxmMoney> cost,
        Optional<String> requiredPermission,
        long visitors,
        boolean locked,
        boolean passwordProtected,
        Optional<String> category,
        Optional<String> icon) {

    public UxmWarp {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(requiredPermission, "requiredPermission");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(icon, "icon");
    }

    /** Whether a visit costs nothing, which is the common case and reads better than comparing to zero. */
    public boolean isFree() {
        return cost.isEmpty();
    }
}
