package com.uxplima.uxmessentials.persistence.homes;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.HomesRecord;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code homes} row and the domain {@link Home}. UUIDs are stored as
 * their canonical 36-character text and the creation time as epoch milliseconds, so the column shape is
 * identical on every backend; this class is the single place that translation lives.
 *
 * <p>The owner name is not persisted (only the uuid is), so a {@link Home} rebuilt from a row carries the
 * owner uuid with the name the caller already holds — the repository passes the queried {@link PlayerRef}
 * through rather than inventing a display name from the row.
 */
final class HomeRows {

    private HomeRows() {}

    /** Rebuild a {@link Home} from a queried row, attributing it to the already-resolved {@code owner}. */
    static Home toHome(Record row, PlayerRef owner) {
        WorldRef world = new WorldRef(
                UUID.fromString(row.get(com.uxplima.uxmessentials.persistence.jooq.tables.Homes.HOMES.WORLD)),
                row.get(com.uxplima.uxmessentials.persistence.jooq.tables.Homes.HOMES.WORLD_NAME));
        var homes = com.uxplima.uxmessentials.persistence.jooq.tables.Homes.HOMES;
        Position position = new Position(
                world, row.get(homes.X), row.get(homes.Y), row.get(homes.Z), row.get(homes.YAW), row.get(homes.PITCH));
        return new Home(
                owner, HomeName.of(row.get(homes.NAME)), position, Instant.ofEpochMilli(row.get(homes.CREATED_AT)));
    }

    /** Populate a {@link HomesRecord} from a domain {@link Home} for an upsert. */
    static void apply(HomesRecord record, Home home) {
        Position location = home.location();
        record.setOwner(home.owner().uuid().toString())
                .setName(home.name().value())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setCreatedAt(home.createdAt().toEpochMilli());
    }
}
