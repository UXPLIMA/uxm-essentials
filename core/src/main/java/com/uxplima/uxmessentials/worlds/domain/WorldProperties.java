package com.uxplima.uxmessentials.worlds.domain;

import java.math.BigDecimal;
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
    public static final WorldProperty<Boolean> ACCESS_RESTRICTED = WorldProperty.ofBoolean("access-restricted", false);
    public static final WorldProperty<Integer> PLAYER_LIMIT = WorldProperty.ofInteger("player-limit", 0);
    public static final WorldProperty<BigDecimal> ENTRY_FEE = WorldProperty.ofDecimal("entry-fee");
    public static final WorldProperty<String> PORTAL_NETHER_LINK = WorldProperty.ofString("portal-nether-link", "");
    public static final WorldProperty<String> PORTAL_END_LINK = WorldProperty.ofString("portal-end-link", "");
    public static final WorldProperty<VoidRescueChain> VOID_RESCUE = WorldProperty.ofChain("void-rescue");
    public static final WorldProperty<Optional<Integer>> VOID_RESCUE_Y =
            WorldProperty.ofOptionalInteger("void-rescue-y");

    public static final List<WorldProperty<?>> ALL = List.of(
            PVP,
            DIFFICULTY,
            FORCE_GAMEMODE,
            SPAWN_ANIMALS,
            SPAWN_MONSTERS,
            TIME,
            WEATHER,
            ACCESS_RESTRICTED,
            PLAYER_LIMIT,
            ENTRY_FEE,
            PORTAL_NETHER_LINK,
            PORTAL_END_LINK,
            VOID_RESCUE,
            VOID_RESCUE_Y);

    private WorldProperties() {}

    public static Optional<WorldProperty<?>> byKey(String key) {
        return ALL.stream().filter(p -> p.key().equals(key)).findFirst();
    }
}
