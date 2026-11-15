package com.uxplima.uxmessentials.persistence.worlds;

import static com.uxplima.uxmessentials.persistence.jooq.tables.World.WORLD;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WorldSetting.WORLD_SETTING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.WorldRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

/**
 * jOOQ-backed {@link WorldRepository} over the {@code world} table, scoped to one backend: every read and write but
 * {@link #network()} names this backend's server, so a world of the same name on another backend is never touched.
 */
public final class JooqWorldRepository extends JooqRepository implements WorldRepository {

    private final String server;

    /** {@code server} is this backend's network name; it must not be {@value NetworkWorld#UNSCOPED}. */
    public JooqWorldRepository(DSLContext dsl, String server) {
        super(dsl);
        this.server = Objects.requireNonNull(server, "server");
        if (server.isBlank() || server.equals(NetworkWorld.UNSCOPED)) {
            throw new IllegalArgumentException("worlds cannot be kept for the server '" + server + "'");
        }
    }

    /** The backend this repository reads and writes for. */
    public String server() {
        return server;
    }

    @Override
    public Optional<ManagedWorld> find(WorldName name) {
        return read(dsl -> dsl.selectFrom(WORLD)
                .where(mine().and(WORLD.NAME.eq(name.value())))
                .fetchOptional()
                .map(row -> WorldRows.toWorld(row, loadSettings(dsl, server, name.value()))));
    }

    @Override
    public List<ManagedWorld> all() {
        return read(dsl -> {
            Map<String, Map<String, String>> settings = settingsOf(dsl, WORLD_SETTING.SERVER_ID.eq(server));
            return dsl.selectFrom(WORLD).where(mine()).orderBy(WORLD.NAME.asc()).fetch().stream()
                    .map(row -> WorldRows.toWorld(row, settingsFor(settings, server, row.get(WORLD.NAME))))
                    .toList();
        });
    }

    @Override
    public boolean exists(WorldName name) {
        return read(dsl -> dsl.fetchExists(WORLD, mine().and(WORLD.NAME.eq(name.value()))));
    }

    @Override
    public void save(ManagedWorld world) {
        write(dsl -> {
            upsert(dsl, world);
            dsl.deleteFrom(WORLD_SETTING)
                    .where(WORLD_SETTING.SERVER_ID.eq(server))
                    .and(WORLD_SETTING.WORLD_NAME.eq(world.name().value()))
                    .execute();
            world.settings()
                    .raw()
                    .forEach((key, value) -> dsl.insertInto(WORLD_SETTING)
                            .set(WORLD_SETTING.SERVER_ID, server)
                            .set(WORLD_SETTING.WORLD_NAME, world.name().value())
                            .set(WORLD_SETTING.SETTING_KEY, key)
                            .set(WORLD_SETTING.SETTING_VALUE, value)
                            .execute());
            return null;
        });
    }

    @Override
    public void delete(WorldName name) {
        write(dsl -> {
            dsl.deleteFrom(WORLD_SETTING)
                    .where(WORLD_SETTING.SERVER_ID.eq(server))
                    .and(WORLD_SETTING.WORLD_NAME.eq(name.value()))
                    .execute();
            return dsl.deleteFrom(WORLD)
                    .where(mine().and(WORLD.NAME.eq(name.value())))
                    .execute();
        });
    }

    @Override
    public List<NetworkWorld> network() {
        return read(dsl -> {
            Map<String, Map<String, String>> settings = settingsOf(dsl, DSL.noCondition());
            List<NetworkWorld> worlds = new ArrayList<>();
            for (Record row : dsl.selectFrom(WORLD)
                    .orderBy(WORLD.NAME.asc(), WORLD.SERVER_ID.asc())
                    .fetch()) {
                String owner = row.get(WORLD.SERVER_ID);
                worlds.add(new NetworkWorld(
                        owner, WorldRows.toWorld(row, settingsFor(settings, owner, row.get(WORLD.NAME)))));
            }
            return worlds;
        });
    }

    private Condition mine() {
        return WORLD.SERVER_ID.eq(server);
    }

    private static Map<String, String> loadSettings(DSLContext dsl, String server, String worldName) {
        return dsl.selectFrom(WORLD_SETTING)
                .where(WORLD_SETTING.SERVER_ID.eq(server))
                .and(WORLD_SETTING.WORLD_NAME.eq(worldName))
                .fetchMap(WORLD_SETTING.SETTING_KEY, WORLD_SETTING.SETTING_VALUE);
    }

    /** Every setting matching {@code where} in one read, keyed by server and world, so a list costs two queries. */
    private static Map<String, Map<String, String>> settingsOf(DSLContext dsl, Condition where) {
        Map<String, Map<String, String>> byWorld = new HashMap<>();
        dsl.selectFrom(WORLD_SETTING)
                .where(where)
                .forEach(row -> byWorld.computeIfAbsent(
                                settingsKey(row.getServerId(), row.getWorldName()), ignored -> new HashMap<>())
                        .put(row.getSettingKey(), row.getSettingValue()));
        return byWorld;
    }

    private static Map<String, String> settingsFor(
            Map<String, Map<String, String>> settings, String server, String worldName) {
        return settings.getOrDefault(settingsKey(server, worldName), Map.of());
    }

    private static String settingsKey(String server, String worldName) {
        return server + '\u0000' + worldName;
    }

    private void upsert(DSLContext dsl, ManagedWorld world) {
        WorldRecord record = dsl.newRecord(WORLD);
        WorldRows.apply(record, world);
        record.setServerId(server);
        dsl.insertInto(WORLD)
                .set(record)
                .onConflict(WORLD.SERVER_ID, WORLD.NAME)
                .doUpdate()
                .set(WORLD.UID, record.getUid())
                .set(WORLD.ENVIRONMENT, record.getEnvironment())
                .set(WORLD.WORLD_TYPE, record.getWorldType())
                .set(WORLD.SEED, record.getSeed())
                .set(WORLD.GENERATOR_REF, record.getGeneratorRef())
                .set(WORLD.DIMENSION, record.getDimension())
                .set(WORLD.GENERATE_STRUCTURES, record.getGenerateStructures())
                .set(WORLD.ALIAS, record.getAlias())
                .set(WORLD.AUTO_LOAD, record.getAutoLoad())
                .set(WORLD.ADOPTED, record.getAdopted())
                .set(WORLD.CREATED_AT, record.getCreatedAt())
                .set(WORLD.CREATED_BY, record.getCreatedBy())
                .execute();
    }
}
