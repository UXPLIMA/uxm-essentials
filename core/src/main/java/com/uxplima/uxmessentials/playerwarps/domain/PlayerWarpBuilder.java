package com.uxplima.uxmessentials.playerwarps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * A mutable builder for {@link PlayerWarp}, kept package-private so it is purely an internal mechanism: each
 * {@link PlayerWarp} transition reads {@link PlayerWarp#toBuilder()}, changes the one or two fields it owns, and
 * calls {@link #build()} — which routes through the canonical {@code PlayerWarp} constructor, so every null-check
 * still fires. It seeds every component from the source warp and exposes a setter only for the components the
 * transitions actually vary; a T5/T6 transition that needs to edit another facet adds its setter here rather than
 * hand-copying all twenty-odd fields at the call site.
 */
final class PlayerWarpBuilder {

    private Optional<PlayerWarpId> id;
    private final PlayerRef owner;
    private final String ownerName;
    private final PlayerWarpName name;
    private Optional<DisplayName> displayName;
    private Position location;
    private final Optional<String> serverId;
    private final Optional<String> categoryId;
    private final Optional<WarpDescription> description;
    private final Optional<IconSpec> icon;
    private WarpAccess access;
    private final boolean passwordSet;
    private final WarpStatus status;
    private final WarpCost price;
    private final WarpEarnings earnings;
    private final RatingSummary ratings;
    private final VisitSummary visits;
    private final int favouriteCount;
    private final Optional<Sponsorship> sponsorship;
    private final Optional<RentState> rent;
    private final WarpEffects effects;
    private final WarpTimingOverrides timing;
    private final Instant createdAt;
    private Instant updatedAt;

    PlayerWarpBuilder(PlayerWarp source) {
        Objects.requireNonNull(source, "source");
        this.id = source.id();
        this.owner = source.owner();
        this.ownerName = source.ownerName();
        this.name = source.name();
        this.displayName = source.displayName();
        this.location = source.location();
        this.serverId = source.serverId();
        this.categoryId = source.categoryId();
        this.description = source.description();
        this.icon = source.icon();
        this.access = source.access();
        this.passwordSet = source.passwordSet();
        this.status = source.status();
        this.price = source.price();
        this.earnings = source.earnings();
        this.ratings = source.ratings();
        this.visits = source.visits();
        this.favouriteCount = source.favouriteCount();
        this.sponsorship = source.sponsorship();
        this.rent = source.rent();
        this.effects = source.effects();
        this.timing = source.timing();
        this.createdAt = source.createdAt();
        this.updatedAt = source.updatedAt();
    }

    PlayerWarpBuilder id(Optional<PlayerWarpId> value) {
        this.id = value;
        return this;
    }

    PlayerWarpBuilder displayName(Optional<DisplayName> value) {
        this.displayName = value;
        return this;
    }

    PlayerWarpBuilder location(Position value) {
        this.location = value;
        return this;
    }

    PlayerWarpBuilder access(WarpAccess value) {
        this.access = value;
        return this;
    }

    PlayerWarpBuilder updatedAt(Instant value) {
        this.updatedAt = value;
        return this;
    }

    PlayerWarp build() {
        return new PlayerWarp(
                id,
                owner,
                ownerName,
                name,
                displayName,
                location,
                serverId,
                categoryId,
                description,
                icon,
                access,
                passwordSet,
                status,
                price,
                earnings,
                ratings,
                visits,
                favouriteCount,
                sponsorship,
                rent,
                effects,
                timing,
                createdAt,
                updatedAt);
    }
}
