package com.uxplima.uxmessentials.persistence.teleport;

import static com.uxplima.uxmessentials.persistence.jooq.tables.TeleportMainSpawn.TELEPORT_MAIN_SPAWN;
import static com.uxplima.uxmessentials.persistence.jooq.tables.TeleportNamedSpawns.TELEPORT_NAMED_SPAWNS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.TeleportSpawnMirrors.TELEPORT_SPAWN_MIRRORS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.TeleportSpawns.TELEPORT_SPAWNS;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportMainSpawnRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportNamedSpawnsRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportSpawnMirrorsRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportSpawnsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.persistence.runtime.StoredScope;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The jOOQ-backed {@link SpawnDirectory} over the V10 spawn tables: a per-world spawn keyed by world uid, the
 * network-wide main spawn pinned at id 0 and one main spawn row per server scope, named spawns keyed by canonical
 * lowercase name, and per-world mirror redirects. Every read is a single keyed lookup and every write upserts on
 * the key, so a re-anchor overwrites the same row rather than inserting. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 *
 * <p>This store has no vanilla-world fallback: {@link #defaultSpawn} returns only the operator-set per-world
 * row, the same as {@link #operatorSpawn}. The vanilla last-resort lives in the bukkit-adapter's decorator,
 * which keeps the {@code org.bukkit} world read out of this module. {@code created_at} is a write-only audit
 * column the domain never reads back.
 */
@NullMarked
public final class JooqSpawnDirectory extends JooqRepository implements SpawnDirectory {

    /** The id of the network-wide main spawn row; scoped rows take the ids after it. */
    private static final int NETWORK_ID = 0;

    public JooqSpawnDirectory(DSLContext dsl) {
        super(dsl);
    }

    /** Load the complete small spawn dataset in one bootstrap repository pass for the authoritative runtime cache. */
    SpawnDirectorySnapshot snapshot() {
        return read(dsl -> {
            Map<UUID, Position> worlds = new LinkedHashMap<>();
            for (TeleportSpawnsRecord row : dsl.selectFrom(TELEPORT_SPAWNS).fetch()) {
                Position position = SpawnRows.toPosition(row);
                worlds.put(position.world().uid(), position);
            }
            Map<String, Position> named = new LinkedHashMap<>();
            for (TeleportNamedSpawnsRecord row :
                    dsl.selectFrom(TELEPORT_NAMED_SPAWNS).fetch()) {
                named.put(row.getName(), SpawnRows.toPosition(row));
            }
            Map<UUID, SpawnMirror> mirrors = new LinkedHashMap<>();
            for (TeleportSpawnMirrorsRecord row :
                    dsl.selectFrom(TELEPORT_SPAWN_MIRRORS).fetch()) {
                SpawnMirror mirror = SpawnRows.toMirror(row);
                mirrors.put(mirror.sourceWorld(), mirror);
            }
            Map<String, Position> mains = new LinkedHashMap<>();
            for (TeleportMainSpawnRecord row :
                    dsl.selectFrom(TELEPORT_MAIN_SPAWN).fetch()) {
                ServerScope scope = StoredScope.read(row.getScope());
                // Only id 0 is network-wide; a stray unscoped row never shadows it.
                if (scope != null || row.getId() == NETWORK_ID) {
                    mains.put(SpawnDirectorySnapshot.mainKey(scope), SpawnRows.toPosition(row));
                }
            }
            return new SpawnDirectorySnapshot(worlds, named, mirrors, mains);
        });
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
    public Optional<Position> mainSpawn(@Nullable ServerScope scope) {
        return read(dsl -> dsl.selectFrom(TELEPORT_MAIN_SPAWN)
                .where(mainRow(scope))
                .fetchOptional()
                .map(SpawnRows::toPosition));
    }

    private static Condition mainRow(@Nullable ServerScope scope) {
        return scope == null ? TELEPORT_MAIN_SPAWN.ID.eq(NETWORK_ID) : TELEPORT_MAIN_SPAWN.SCOPE.eq(scope.value());
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
    public void setMainSpawn(@Nullable ServerScope scope, Position position) {
        Objects.requireNonNull(position, "position");
        long savedAt = System.currentTimeMillis();
        if (scope == null) {
            write(dsl -> {
                upsertMain(dsl, position, savedAt);
                return null;
            });
            return;
        }
        try {
            replaceScopedMain(scope, position, savedAt);
        } catch (PersistenceException collided) {
            // Another server took the same next id, or wrote the same scope, between the read and the insert; the
            // second attempt reads what it committed. A second failure is a real fault and reaches the caller.
            replaceScopedMain(scope, position, savedAt);
        }
    }

    private void replaceScopedMain(ServerScope scope, Position position, long savedAt) {
        write(dsl -> {
            replaceScopedMain(dsl, scope, position, savedAt);
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
        SpawnRows.applyMain(record, NETWORK_ID, null, spawn, savedAt);
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

    /**
     * Replace the main spawn of {@code scope}: its row goes and a new one takes the next free id, in one
     * transaction. The unique index on the scope turns a peer writing the same scope at the same moment into a
     * failed write rather than a second row, and the caller tries once more.
     */
    private static void replaceScopedMain(DSLContext dsl, ServerScope scope, Position spawn, long savedAt) {
        dsl.deleteFrom(TELEPORT_MAIN_SPAWN)
                .where(TELEPORT_MAIN_SPAWN.SCOPE.eq(scope.value()))
                .execute();
        Integer maxId = dsl.select(DSL.max(TELEPORT_MAIN_SPAWN.ID))
                .from(TELEPORT_MAIN_SPAWN)
                .fetchOne(0, Integer.class);
        int id = Math.max(NETWORK_ID, maxId == null ? NETWORK_ID : maxId) + 1;
        TeleportMainSpawnRecord record = dsl.newRecord(TELEPORT_MAIN_SPAWN);
        SpawnRows.applyMain(record, id, scope, spawn, savedAt);
        dsl.insertInto(TELEPORT_MAIN_SPAWN).set(record).execute();
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
