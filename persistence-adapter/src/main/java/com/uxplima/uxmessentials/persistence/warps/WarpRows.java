package com.uxplima.uxmessentials.persistence.warps;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.Warps;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.WarpsRecord;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code warps} row and the domain {@link Warp}. UUIDs are stored as
 * their canonical 36-character text and the creation time as epoch milliseconds, so the column shape is
 * identical on every backend; the optional cost and required-permission columns are nullable, mapping to a
 * {@link WarpCost#free()} cost and an empty {@link Optional} respectively. This class is the single place
 * that translation lives.
 *
 * <p>The owner's display name is not persisted (only the uuid is), so a {@link Warp} rebuilt from a row
 * carries the owner uuid with the uuid string as a placeholder name — {@code /warpinfo} renders the owner
 * from this attribution, and a live name is resolved by the adapter when one is available.
 */
final class WarpRows {

    private static final Warps WARPS = Warps.WARPS;

    private WarpRows() {}

    /** Rebuild a {@link Warp} from a queried row. */
    static Warp toWarp(Record row) {
        WorldRef world = new WorldRef(UUID.fromString(row.get(WARPS.WORLD)), row.get(WARPS.WORLD_NAME));
        Position position = new Position(
                world, row.get(WARPS.X), row.get(WARPS.Y), row.get(WARPS.Z), row.get(WARPS.YAW), row.get(WARPS.PITCH));
        UUID ownerUuid = UUID.fromString(row.get(WARPS.OWNER));
        PlayerRef owner = new PlayerRef(ownerUuid, ownerUuid.toString());
        return new Warp(
                WarpName.of(row.get(WARPS.NAME)),
                position,
                owner,
                Instant.ofEpochMilli(row.get(WARPS.CREATED_AT)),
                cost(row.get(WARPS.COST)),
                Optional.ofNullable(row.get(WARPS.REQUIRED_PERMISSION)));
    }

    /** Populate a {@link WarpsRecord} from a domain {@link Warp} for an upsert. */
    static void apply(WarpsRecord record, Warp warp) {
        Position location = warp.location();
        record.setName(warp.name().value())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setOwner(warp.owner().uuid().toString())
                .setCreatedAt(warp.createdAt().toEpochMilli())
                .setCost(warp.hasCost() ? warp.cost().amount() : null)
                .setRequiredPermission(warp.requiredPermission().orElse(null));
    }

    private static WarpCost cost(BigDecimal stored) {
        // A null cost column is a free warp; a stored amount is the price to use it.
        return stored == null ? WarpCost.free() : WarpCost.of(stored);
    }
}
