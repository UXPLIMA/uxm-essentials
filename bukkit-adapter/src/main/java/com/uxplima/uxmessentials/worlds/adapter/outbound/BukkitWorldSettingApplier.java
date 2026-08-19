package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog.GameRuleType;
import com.uxplima.uxmessentials.worlds.application.port.WorldSettingApplier;
import com.uxplima.uxmessentials.worlds.domain.SpawnCodec;
import com.uxplima.uxmessentials.worlds.domain.WeatherLock;
import com.uxplima.uxmessentials.worlds.domain.WorldDifficulty;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import org.jspecify.annotations.NullMarked;

/**
 * Maps a {@link WorldSettings} bag onto the live world: scalar properties, the time/weather locks
 * (each freezes its vanilla cycle gamerule), the spawn, and the per-world gamerules. The caller runs
 * this on the global region thread. Force-gamemode is per-player and applied elsewhere, not here.
 */
@NullMarked
public final class BukkitWorldSettingApplier implements WorldSettingApplier {

    private final Server server;
    private final GameRuleCatalog catalog;
    private final Logger log;

    public BukkitWorldSettingApplier(Server server, GameRuleCatalog catalog, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.log = Objects.requireNonNull(log, "log");
    }

    // World#setPVP(boolean) is deprecation-flagged but is still the only per-world PVP toggle Paper offers.
    @Override
    @SuppressWarnings("deprecation")
    public void apply(WorldName name, WorldSettings settings) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(settings, "settings");
        World world = server.getWorld(name.value());
        if (world == null) {
            return; // not loaded; settings re-apply on next load
        }
        world.setPVP(settings.get(WorldProperties.PVP));
        world.setDifficulty(toBukkit(settings.get(WorldProperties.DIFFICULTY)));
        world.setAllowMonsterSpawning(settings.get(WorldProperties.SPAWN_MONSTERS));
        // Paper 26.2 removed the animal half of the old spawn flags; AnimalSpawnListener holds that setting now.
        applyTime(world, settings);
        applyWeather(world, settings);
        applySpawn(world, settings);
        applyGamerules(world, settings);
    }

    // The DO_DAYLIGHT_CYCLE GameRule constant is removal-flagged in favour of the namespaced registry,
    // but the typed setGameRule overload still takes it; we keep it until a replacement constant ships.
    @SuppressWarnings("removal")
    private static void applyTime(World world, WorldSettings settings) {
        if (settings.rawValue(WorldProperties.TIME.key()).isPresent()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, Boolean.FALSE);
            world.setTime(settings.get(WorldProperties.TIME));
        } else {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, Boolean.TRUE);
        }
    }

    // DO_WEATHER_CYCLE is removal-flagged like DO_DAYLIGHT_CYCLE above; the typed setGameRule overload
    // still accepts it, so we keep the constant until Paper ships a registry-backed replacement.
    @SuppressWarnings("removal")
    private static void applyWeather(World world, WorldSettings settings) {
        WeatherLock lock = settings.get(WorldProperties.WEATHER);
        if (lock == WeatherLock.NONE) {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, Boolean.TRUE);
            return;
        }
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, Boolean.FALSE);
        switch (lock) {
            case CLEAR -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case RAIN -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case THUNDER -> {
                world.setStorm(true);
                world.setThundering(true);
            }
            case NONE -> {}
        }
    }

    private static void applySpawn(World world, WorldSettings settings) {
        settings.spawn()
                .flatMap(SpawnCodec::parseComponents)
                .ifPresent(c -> world.setSpawnLocation((int) c[0], (int) c[1], (int) c[2], (float) c[3]));
    }

    private void applyGamerules(World world, WorldSettings settings) {
        Map<String, GameRule<?>> byName = gameRulesByName();
        settings.gamerules().forEach((rule, value) -> {
            GameRule<?> gameRule = byName.get(rule);
            if (gameRule == null) {
                return; // version-tolerant: unknown rule skipped
            }
            setRule(world, gameRule, value);
        });
    }

    // GameRule's name-keyed surface (values/getName) is marked for removal in favour of the namespaced
    // Registry, but the vanilla rule names it exposes are what configs and commands store; MockBukkit
    // also leaves Registry.getByName null. We keep that surface until a name-preserving replacement ships.
    @SuppressWarnings({"deprecation", "removal"})
    private static Map<String, GameRule<?>> gameRulesByName() {
        return Arrays.stream(GameRule.values()).collect(Collectors.toMap(GameRule::getName, Function.identity()));
    }

    @SuppressWarnings("unchecked") // GameRule<?> resolved by name; the catalog tells us Boolean vs Integer.
    private void setRule(World world, GameRule<?> rule, String value) {
        try {
            if (catalog.typeOf(ruleName(rule)).orElse(GameRuleType.BOOLEAN) == GameRuleType.INTEGER) {
                world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(value.strip()));
            } else {
                world.setGameRule((GameRule<Boolean>) rule, Boolean.parseBoolean(value));
            }
        } catch (RuntimeException bad) {
            log.warn("Skipping invalid gamerule {} = {}", ruleName(rule), value);
        }
    }

    // Same removal-marked name surface as gameRulesByName; see the note there for why it stays.
    @SuppressWarnings({"deprecation", "removal"})
    private static String ruleName(GameRule<?> rule) {
        return rule.getName();
    }

    private static Difficulty toBukkit(WorldDifficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> Difficulty.PEACEFUL;
            case EASY -> Difficulty.EASY;
            case NORMAL -> Difficulty.NORMAL;
            case HARD -> Difficulty.HARD;
        };
    }
}
