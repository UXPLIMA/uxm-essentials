package com.uxplima.uxmessentials.persistence.worlds;

import static com.uxplima.uxmessentials.persistence.jooq.tables.World.WORLD;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.WorldRecord;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jooq.Record;

/** Maps {@code world} rows to and from {@link ManagedWorld}. Booleans are stored as INT 0/1. */
final class WorldRows {

    private WorldRows() {}

    static ManagedWorld toWorld(Record row) {
        WorldSpec spec = new WorldSpec(
                WorldEnvironment.valueOf(row.get(WORLD.ENVIRONMENT)),
                WorldGenType.valueOf(row.get(WORLD.WORLD_TYPE)),
                Optional.ofNullable(row.get(WORLD.SEED)),
                Optional.ofNullable(row.get(WORLD.GENERATOR_REF)).map(GeneratorRef::of),
                row.get(WORLD.GENERATE_STRUCTURES) != 0,
                // The dimension column is present but not yet mapped to a VO use; a later sub-project parses it.
                Optional.empty());
        return new ManagedWorld(
                WorldName.of(row.get(WORLD.NAME)),
                spec,
                Optional.ofNullable(row.get(WORLD.ALIAS)),
                row.get(WORLD.AUTO_LOAD) != 0,
                row.get(WORLD.ADOPTED) != 0,
                Optional.ofNullable(row.get(WORLD.UID)).map(UUID::fromString),
                Instant.ofEpochMilli(row.get(WORLD.CREATED_AT)),
                Optional.ofNullable(row.get(WORLD.CREATED_BY)).map(UUID::fromString));
    }

    static void apply(WorldRecord record, ManagedWorld world) {
        WorldSpec spec = world.spec();
        record.setName(world.name().value());
        record.setUid(world.knownUid().map(UUID::toString).orElse(null));
        record.setEnvironment(spec.environment().name());
        record.setWorldType(spec.worldType().name());
        record.setSeed(spec.seed().orElse(null));
        record.setGeneratorRef(spec.generator().map(GeneratorRef::value).orElse(null));
        record.setDimension(spec.dimension().map(d -> d.value()).orElse(null));
        record.setGenerateStructures(spec.generateStructures() ? 1 : 0);
        record.setAlias(world.alias().orElse(null));
        record.setAutoLoad(world.autoLoad() ? 1 : 0);
        record.setAdopted(world.adopted() ? 1 : 0);
        record.setCreatedAt(world.createdAt().toEpochMilli());
        record.setCreatedBy(world.createdBy().map(UUID::toString).orElse(null));
    }
}
