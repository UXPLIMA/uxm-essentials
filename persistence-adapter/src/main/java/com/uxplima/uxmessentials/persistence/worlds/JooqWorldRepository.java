package com.uxplima.uxmessentials.persistence.worlds;

import static com.uxplima.uxmessentials.persistence.jooq.tables.World.WORLD;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WorldSetting.WORLD_SETTING;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.WorldRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jooq.DSLContext;

/** jOOQ-backed {@link WorldRepository} over the {@code world} table. */
public final class JooqWorldRepository extends JooqRepository implements WorldRepository {

    public JooqWorldRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<ManagedWorld> find(WorldName name) {
        return read(dsl -> dsl.selectFrom(WORLD)
                .where(WORLD.NAME.eq(name.value()))
                .fetchOptional()
                .map(row -> WorldRows.toWorld(row, loadSettings(dsl, name.value()))));
    }

    @Override
    public List<ManagedWorld> all() {
        return read(dsl -> dsl.selectFrom(WORLD)
                .orderBy(WORLD.NAME.asc())
                .fetch()
                .map(row -> WorldRows.toWorld(row, loadSettings(dsl, row.get(WORLD.NAME)))));
    }

    @Override
    public boolean exists(WorldName name) {
        return read(dsl -> dsl.fetchExists(WORLD, WORLD.NAME.eq(name.value())));
    }

    @Override
    public void save(ManagedWorld world) {
        write(dsl -> {
            upsert(dsl, world);
            dsl.deleteFrom(WORLD_SETTING)
                    .where(WORLD_SETTING.WORLD_NAME.eq(world.name().value()))
                    .execute();
            world.settings()
                    .raw()
                    .forEach((key, value) -> dsl.insertInto(WORLD_SETTING)
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
                    .where(WORLD_SETTING.WORLD_NAME.eq(name.value()))
                    .execute();
            return dsl.deleteFrom(WORLD).where(WORLD.NAME.eq(name.value())).execute();
        });
    }

    private static Map<String, String> loadSettings(DSLContext dsl, String worldName) {
        return dsl.selectFrom(WORLD_SETTING)
                .where(WORLD_SETTING.WORLD_NAME.eq(worldName))
                .fetchMap(WORLD_SETTING.SETTING_KEY, WORLD_SETTING.SETTING_VALUE);
    }

    private static void upsert(DSLContext dsl, ManagedWorld world) {
        WorldRecord record = dsl.newRecord(WORLD);
        WorldRows.apply(record, world);
        dsl.insertInto(WORLD)
                .set(record)
                .onConflict(WORLD.NAME)
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
