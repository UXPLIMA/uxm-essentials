package com.uxplima.uxmessentials.persistence.teleport;

import static com.uxplima.uxmessentials.persistence.jooq.tables.TeleportMainSpawn.TELEPORT_MAIN_SPAWN;
import static com.uxplima.uxmessentials.persistence.jooq.tables.TeleportNamedSpawns.TELEPORT_NAMED_SPAWNS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.TeleportSpawnMirrors.TELEPORT_SPAWN_MIRRORS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.TeleportSpawns.TELEPORT_SPAWNS;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportMainSpawnRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportNamedSpawnsRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportSpawnMirrorsRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportSpawnsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link SpawnDirectory} over the V10 spawn tables: a per-world spawn keyed by world uid, the
 * singleton main spawn pinned at id 0, named spawns keyed by canonical lowercase name, and per-world mirror
 * redirects. Every read is a single keyed lookup and every write upserts on the key, so a re-anchor overwrites
 * the same row rather than inserting. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 *
 * <p>This store has no vanilla-world fallback: {@link #defaultSpawn} returns only the operator-set per-world
 * row, the same as {@link #operatorSpawn}. The vanilla last-resort lives in the bukkit-adapter's decorator,
 * which keeps the {@code org.bukkit} world read out of this module. {@code created_at} is a write-only audit
 * column the domain never reads back.
 */
@NullMarked
public final class JooqSpawnDirectory extends JooqRepository implements SpawnDirectory {

    public JooqSpawnDirectory(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Position> defaultSpawn(WorldRef world) {
        return operatorSpawn(world);
    }

    @Override
    public Optional<Position> operatorSpawn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return read(dsl -> dsl.selectFrom(TELEPORT_SPAWNS)
                .where(TELEPORT_SPAWNS.WORLD.eq(world.uid().toString()))
                .fetchOptional()
                .map(SpawnRows::toPosition));
    }

    @Override
    public Optional<Position> mainSpawn() {
        return read(dsl -> dsl.selectFrom(TELEPORT_MAIN_SPAWN)
                .where(TELEPORT_MAIN_SPAWN.ID.eq(0))
                .fetchOptional()
                .map(SpawnRows::toPosition));
    }

    @Override
    public Optional<Position> namedSpawn(String name) {
        Objects.requireNonNull(name, "name");
        String key = normalise(name);
        return read(dsl -> dsl.selectFrom(TELEPORT_NAMED_SPAWNS)
                .where(TELEPORT_NAMED_SPAWNS.NAME.eq(key))
                .fetchOptional()
                .map(SpawnRows::toPosition));
    }

    @Override
    public Optional<SpawnMirror> mirrorFor(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return read(dsl -> dsl.selectFrom(TELEPORT_SPAWN_MIRRORS)
                .where(TELEPORT_SPAWN_MIRRORS.SOURCE_WORLD.eq(world.uid().toString()))
                .fetchOptional()
                .map(SpawnRows::toMirror));
    }

    @Override
    public void setDefaultSpawn(WorldRef world, Position position) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        long savedAt = System.currentTimeMillis();
        write(dsl -> {
            upsertWorld(dsl, position, savedAt);
            return null;
        });
    }

    @Override
    public void setMainSpawn(Position position) {
        Objects.requireNonNull(position, "position");
        long savedAt = System.currentTimeMillis();
        write(dsl -> {
            upsertMain(dsl, position, savedAt);
            return null;
        });
    }

    @Override
    public void setNamedSpawn(String name, Position position) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        String key = normalise(name);
        long savedAt = System.currentTimeMillis();
        write(dsl -> {
            upsertNamed(dsl, key, position, savedAt);
            return null;
        });
    }

    @Override
    public boolean removeDefaultSpawn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return write(dsl -> dsl.deleteFrom(TELEPORT_SPAWNS)
                        .where(TELEPORT_SPAWNS.WORLD.eq(world.uid().toString()))
                        .execute()
                > 0);
    }

    @Override
    public void setMirror(SpawnMirror mirror) {
        Objects.requireNonNull(mirror, "mirror");
        long savedAt = System.currentTimeMillis();
        write(dsl -> {
            upsertMirror(dsl, mirror, savedAt);
            return null;
        });
    }

    private static void upsertWorld(DSLContext dsl, Position spawn, long savedAt) {
        TeleportSpawnsRecord record = dsl.newRecord(TELEPORT_SPAWNS);
        SpawnRows.apply(record, spawn, savedAt);
        dsl.insertInto(TELEPORT_SPAWNS)
                .set(record)
                .onConflict(TELEPORT_SPAWNS.WORLD)
                .doUpdate()
                .set(TELEPORT_SPAWNS.WORLD_NAME, record.getWorldName())
                .set(TELEPORT_SPAWNS.X, record.getX())
                .set(TELEPORT_SPAWNS.Y, record.getY())
                .set(TELEPORT_SPAWNS.Z, record.getZ())
                .set(TELEPORT_SPAWNS.YAW, record.getYaw())
                .set(TELEPORT_SPAWNS.PITCH, record.getPitch())
                .set(TELEPORT_SPAWNS.CREATED_AT, record.getCreatedAt())
                .execute();
    }

    private static void upsertMain(DSLContext dsl, Position spawn, long savedAt) {
        TeleportMainSpawnRecord record = dsl.newRecord(TELEPORT_MAIN_SPAWN);
        SpawnRows.applyMain(record, spawn, savedAt);
        dsl.insertInto(TELEPORT_MAIN_SPAWN)
                .set(record)
                .onConflict(TELEPORT_MAIN_SPAWN.ID)
                .doUpdate()
                .set(TELEPORT_MAIN_SPAWN.WORLD, record.getWorld())
                .set(TELEPORT_MAIN_SPAWN.WORLD_NAME, record.getWorldName())
                .set(TELEPORT_MAIN_SPAWN.X, record.getX())
                .set(TELEPORT_MAIN_SPAWN.Y, record.getY())
                .set(TELEPORT_MAIN_SPAWN.Z, record.getZ())
                .set(TELEPORT_MAIN_SPAWN.YAW, record.getYaw())
                .set(TELEPORT_MAIN_SPAWN.PITCH, record.getPitch())
                .set(TELEPORT_MAIN_SPAWN.CREATED_AT, record.getCreatedAt())
                .execute();
    }

    private static void upsertNamed(DSLContext dsl, String name, Position spawn, long savedAt) {
        TeleportNamedSpawnsRecord record = dsl.newRecord(TELEPORT_NAMED_SPAWNS);
        SpawnRows.applyNamed(record, name, spawn, savedAt);
        dsl.insertInto(TELEPORT_NAMED_SPAWNS)
                .set(record)
                .onConflict(TELEPORT_NAMED_SPAWNS.NAME)
                .doUpdate()
                .set(TELEPORT_NAMED_SPAWNS.WORLD, record.getWorld())
                .set(TELEPORT_NAMED_SPAWNS.WORLD_NAME, record.getWorldName())
                .set(TELEPORT_NAMED_SPAWNS.X, record.getX())
                .set(TELEPORT_NAMED_SPAWNS.Y, record.getY())
                .set(TELEPORT_NAMED_SPAWNS.Z, record.getZ())
                .set(TELEPORT_NAMED_SPAWNS.YAW, record.getYaw())
                .set(TELEPORT_NAMED_SPAWNS.PITCH, record.getPitch())
                .set(TELEPORT_NAMED_SPAWNS.CREATED_AT, record.getCreatedAt())
                .execute();
    }

    private static void upsertMirror(DSLContext dsl, SpawnMirror mirror, long savedAt) {
        TeleportSpawnMirrorsRecord record = dsl.newRecord(TELEPORT_SPAWN_MIRRORS);
        SpawnRows.applyMirror(record, mirror, savedAt);
        dsl.insertInto(TELEPORT_SPAWN_MIRRORS)
                .set(record)
                .onConflict(TELEPORT_SPAWN_MIRRORS.SOURCE_WORLD)
                .doUpdate()
                .set(TELEPORT_SPAWN_MIRRORS.TARGET_WORLD, record.getTargetWorld())
                .set(TELEPORT_SPAWN_MIRRORS.CREATED_AT, record.getCreatedAt())
                .execute();
    }

    private static String normalise(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
