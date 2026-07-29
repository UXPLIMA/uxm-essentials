package com.uxplima.uxmessentials.migration.convert.multiverse.map;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.convert.map.ImportedWorld;
import com.uxplima.uxmessentials.migration.convert.multiverse.parse.MultiverseSpawn;
import com.uxplima.uxmessentials.migration.convert.multiverse.parse.MultiverseWorld;
import com.uxplima.uxmessentials.worlds.domain.ForcedGameMode;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.SpawnCodec;
import com.uxplima.uxmessentials.worlds.domain.WorldDifficulty;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldProperty;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jspecify.annotations.NullMarked;

/**
 * The Anti-Corruption Layer between a Multiverse world entry and our {@link ManagedWorld} aggregate
 * (docs/12-migration §5). Multiverse's creation facts (environment, seed, generator) become the immutable
 * {@link WorldSpec}; its alias and auto-load become the management facets; the rest becomes typed
 * {@link WorldSettings} rows through the same {@link WorldProperties} catalog {@code /world set} writes.
 *
 * <p>A world whose name our registry cannot express is dropped rather than renamed. {@link WorldName} is
 * constrained to a safe folder-name shape, so a Multiverse entry carrying a dot or a path character names a world our
 * own {@code /world} commands could never address either; mapping it under a mangled name would create a registry
 * row pointing at nothing.
 *
 * <p>A value Multiverse holds at its own default is not written as a setting. Our settings bag stores only what an
 * operator has actually chosen, so importing every default would turn Multiverse's defaults into explicit overrides
 * of ours. Multiverse's {@code -1} player limit is its "no limit", which is our {@code 0}, and is likewise left off.
 */
@NullMarked
public final class MultiverseWorldMapper {

    /** Multiverse's "no player limit" sentinel, which our catalog spells as the default {@code 0}. */
    private static final int NO_PLAYER_LIMIT = -1;

    private final Clock clock;

    public MultiverseWorldMapper(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Map one entry, or empty when its name is not one our world registry can hold. */
    public Optional<ImportedWorld> map(MultiverseWorld world) {
        Objects.requireNonNull(world, "world");
        return name(world.name())
                .map(name -> new ImportedWorld(new ManagedWorld(
                        name,
                        spec(world),
                        world.alias(),
                        world.autoLoad().orElse(true),
                        true,
                        Optional.empty(),
                        clock.instant(),
                        Optional.empty(),
                        settings(world))));
    }

    private static Optional<WorldName> name(String raw) {
        try {
            return Optional.of(WorldName.of(raw));
        } catch (IllegalArgumentException notOurShape) {
            return Optional.empty();
        }
    }

    private static WorldSpec spec(MultiverseWorld world) {
        return new WorldSpec(
                world.environment()
                        .flatMap(value -> constant(value, WorldEnvironment.class))
                        .orElse(WorldEnvironment.NORMAL),
                WorldGenType.NORMAL,
                world.seed(),
                world.generator().map(GeneratorRef::of),
                true,
                Optional.empty());
    }

    private static WorldSettings settings(MultiverseWorld world) {
        WorldSettings settings = WorldSettings.defaults();
        settings = apply(settings, WorldProperties.PVP, world.pvp());
        settings = apply(
                settings,
                WorldProperties.DIFFICULTY,
                world.difficulty().flatMap(value -> constant(value, WorldDifficulty.class)));
        settings = apply(
                settings,
                WorldProperties.FORCE_GAMEMODE,
                world.gameMode().flatMap(value -> constant(value, ForcedGameMode.class)));
        settings = apply(
                settings, WorldProperties.PLAYER_LIMIT, world.playerLimit().filter(limit -> limit > NO_PLAYER_LIMIT));
        settings = apply(settings, WorldProperties.ENTRY_FEE, world.entryFee().map(BigDecimal::valueOf));
        WorldSettings withProperties = settings;
        return world.spawn()
                .map(spawn -> withProperties.withRaw(WorldSettings.spawnKey(), encode(spawn)))
                .orElse(withProperties);
    }

    private static <T> WorldSettings apply(WorldSettings settings, WorldProperty<T> property, Optional<T> value) {
        return value.filter(chosen -> !chosen.equals(property.defaultValue()))
                .map(chosen -> settings.with(property, chosen))
                .orElse(settings);
    }

    /** The world spawn in the settings bag's world-implied form, through the domain's own codec. */
    private static String encode(MultiverseSpawn spawn) {
        return SpawnCodec.encode(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    /** An enum constant by name, or empty when Multiverse names one our catalog does not have. */
    private static <E extends Enum<E>> Optional<E> constant(String name, Class<E> type) {
        try {
            return Optional.of(Enum.valueOf(type, name));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
