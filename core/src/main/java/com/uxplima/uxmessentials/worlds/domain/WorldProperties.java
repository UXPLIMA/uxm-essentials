package com.uxplima.uxmessentials.worlds.domain;

import java.util.List;
import java.util.Optional;

/** The registry of scalar per-world properties. Gamerules are handled separately (generic). */
public final class WorldProperties {

    public static final WorldProperty<Boolean> PVP = WorldProperty.ofBoolean("pvp", true);
    public static final WorldProperty<WorldDifficulty> DIFFICULTY =
            WorldProperty.ofEnum("difficulty", WorldDifficulty.NORMAL, WorldDifficulty.class);
    public static final WorldProperty<ForcedGameMode> FORCE_GAMEMODE =
            WorldProperty.ofEnum("force-gamemode", ForcedGameMode.NONE, ForcedGameMode.class);
    public static final WorldProperty<Boolean> SPAWN_ANIMALS = WorldProperty.ofBoolean("spawn-animals", true);
    public static final WorldProperty<Boolean> SPAWN_MONSTERS = WorldProperty.ofBoolean("spawn-monsters", true);
    public static final WorldProperty<Long> TIME = WorldProperty.ofTicks("time");
    public static final WorldProperty<WeatherLock> WEATHER =
            WorldProperty.ofEnum("weather", WeatherLock.NONE, WeatherLock.class);

    public static final List<WorldProperty<?>> ALL =
            List.of(PVP, DIFFICULTY, FORCE_GAMEMODE, SPAWN_ANIMALS, SPAWN_MONSTERS, TIME, WEATHER);

    private WorldProperties() {}

    public static Optional<WorldProperty<?>> byKey(String key) {
        return ALL.stream().filter(p -> p.key().equals(key)).findFirst();
    }
}
