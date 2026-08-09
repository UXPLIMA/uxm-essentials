package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A warp a player owns and (usually) shares with the rest of the server.
 *
 * <p>Everything a player warp browser shows is here, and nothing that would let a consumer bypass its rules: the
 * password is published as a flag rather than a value, and the whitelist is not published at all, because knowing
 * who is on it is the owner's business.
 *
 * @param id the storage id, stable for the life of the warp
 * @param name the warp's id, which is also what a player types
 * @param displayName the styled name the owner chose, or empty when they kept the plain one
 * @param ownerId the owning player
 * @param ownerName the owner's name as last recorded
 * @param location where it points
 * @param serverId the server it lives on when the network is running cross-server, or empty on a single server
 * @param category the category id it was filed under, or empty
 * @param description the blurb the owner wrote, or empty
 * @param icon the material id or head spec of its menu icon, or empty for the default
 * @param access who may use it
 * @param passwordProtected whether a password is set (the password itself is never published)
 * @param status whether it is usable at all
 * @param price what a visit charges, or empty when the warp is free
 * @param averageRating the mean player rating, zero when nobody has rated it
 * @param ratingCount how many players have rated it
 * @param visits how many visits it has recorded
 * @param uniqueVisitors how many distinct players have visited
 * @param favourites how many players have favourited it
 * @param sponsoredUntil when its paid promotion runs out, or empty when it is not sponsored
 * @param rentPaidUntil when its rent runs out, or empty when the server does not charge rent
 * @param createdAt when the owner created it
 * @param updatedAt when it was last changed
 */
public record UxmPlayerWarp(
        long id,
        String name,
        Optional<String> displayName,
        UUID ownerId,
        String ownerName,
        UxmLocation location,
        Optional<String> serverId,
        Optional<String> category,
        Optional<String> description,
        Optional<String> icon,
        UxmPlayerWarpAccess access,
        boolean passwordProtected,
        UxmPlayerWarpStatus status,
        Optional<UxmMoney> price,
        double averageRating,
        int ratingCount,
        long visits,
        int uniqueVisitors,
        int favourites,
        Optional<Instant> sponsoredUntil,
        Optional<Instant> rentPaidUntil,
        Instant createdAt,
        Instant updatedAt) {

    public UxmPlayerWarp {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(sponsoredUntil, "sponsoredUntil");
        Objects.requireNonNull(rentPaidUntil, "rentPaidUntil");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** The styled name if the owner set one, otherwise the plain name, which is what the menus show. */
    public String label() {
        return displayName.orElse(name);
    }

    /** Whether it is currently promoted, which is what decides its place in a sorted list. */
    public boolean isSponsored() {
        return sponsoredUntil.isPresent();
    }
}
