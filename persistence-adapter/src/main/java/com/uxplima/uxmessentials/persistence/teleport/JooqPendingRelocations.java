package com.uxplima.uxmessentials.persistence.teleport;

import static com.uxplima.uxmessentials.persistence.jooq.tables.TeleportRelocations.TELEPORT_RELOCATIONS;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportRelocationsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.teleport.application.port.PendingRelocations;
import com.uxplima.uxmessentials.teleport.domain.PendingRelocation;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link PendingRelocations} over the V102 {@code teleport_relocations} table, one row per player and
 * backend. A save upserts on that key, so a later {@code /tpofflinehere} replaces an earlier one; a claim is one
 * conditional {@code DELETE} on the key, the instant and the staff member, so the database decides which of two
 * quick joins removed the row and a row replaced meanwhile is left alone; a restore inserts only when no row is there.
 */
@NullMarked
final class JooqPendingRelocations extends JooqRepository implements PendingRelocations {

    JooqPendingRelocations(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void save(PendingRelocation relocation) {
        TeleportRelocationsRecord row = toRow(relocation);
        write(dsl -> dsl.insertInto(TELEPORT_RELOCATIONS)
                .set(row)
                .onConflict(TELEPORT_RELOCATIONS.PLAYER_UUID, TELEPORT_RELOCATIONS.SERVER_ID)
                .doUpdate()
                .set(TELEPORT_RELOCATIONS.WORLD, row.getWorld())
                .set(TELEPORT_RELOCATIONS.X, row.getX())
                .set(TELEPORT_RELOCATIONS.Y, row.getY())
                .set(TELEPORT_RELOCATIONS.Z, row.getZ())
                .set(TELEPORT_RELOCATIONS.YAW, row.getYaw())
                .set(TELEPORT_RELOCATIONS.PITCH, row.getPitch())
                .set(TELEPORT_RELOCATIONS.CREATED_AT, row.getCreatedAt())
                .set(TELEPORT_RELOCATIONS.ACTOR, row.getActor())
                .execute());
    }

    @Override
    public Optional<PendingRelocation> find(UUID player, String server) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        return read(dsl -> dsl.selectFrom(TELEPORT_RELOCATIONS)
                .where(TELEPORT_RELOCATIONS.PLAYER_UUID.eq(player.toString()))
                .and(TELEPORT_RELOCATIONS.SERVER_ID.eq(server))
                .fetchOptional()
                .map(JooqPendingRelocations::toRelocation));
    }

    @Override
    public boolean claim(PendingRelocation relocation) {
        Objects.requireNonNull(relocation, "relocation");
        int removed = write(dsl -> dsl.deleteFrom(TELEPORT_RELOCATIONS)
                .where(TELEPORT_RELOCATIONS.PLAYER_UUID.eq(relocation.player().toString()))
                .and(TELEPORT_RELOCATIONS.SERVER_ID.eq(relocation.server()))
                .and(TELEPORT_RELOCATIONS.CREATED_AT.eq(relocation.createdAt().toEpochMilli()))
                .and(TELEPORT_RELOCATIONS.ACTOR.eq(relocation.actor().toString()))
                .execute());
        return removed == 1;
    }

    @Override
    public void restore(PendingRelocation relocation) {
        TeleportRelocationsRecord row = toRow(relocation);
        write(dsl -> dsl.insertInto(TELEPORT_RELOCATIONS)
                .set(row)
                .onConflictDoNothing()
                .execute());
    }

    private static TeleportRelocationsRecord toRow(PendingRelocation relocation) {
        Objects.requireNonNull(relocation, "relocation");
        TeleportRelocationsRecord row = new TeleportRelocationsRecord();
        row.setPlayerUuid(relocation.player().toString());
        row.setServerId(relocation.server());
        row.setWorld(relocation.world().toString());
        row.setX(relocation.x());
        row.setY(relocation.y());
        row.setZ(relocation.z());
        row.setYaw(relocation.yaw());
        row.setPitch(relocation.pitch());
        row.setCreatedAt(relocation.createdAt().toEpochMilli());
        row.setActor(relocation.actor().toString());
        return row;
    }

    private static PendingRelocation toRelocation(TeleportRelocationsRecord row) {
        return new PendingRelocation(
                UUID.fromString(row.getPlayerUuid()),
                row.getServerId(),
                UUID.fromString(row.getWorld()),
                row.getX(),
                row.getY(),
                row.getZ(),
                row.getYaw(),
                row.getPitch(),
                Instant.ofEpochMilli(row.getCreatedAt()),
                UUID.fromString(row.getActor()));
    }
}
