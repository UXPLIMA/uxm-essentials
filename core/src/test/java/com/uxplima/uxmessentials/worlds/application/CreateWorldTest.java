package com.uxplima.uxmessentials.worlds.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.worlds.domain.ForcedGameMode;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldCreateOptions;
import com.uxplima.uxmessentials.worlds.domain.WorldDifficulty;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/** A world made through the plugin takes the {@code defaults} and {@code settings.defaults} blocks of the config. */
class CreateWorldTest {

    private static final WorldName GAMES = WorldName.of("games");

    private final InMemoryWorlds repository = new InMemoryWorlds();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final RecordingNotifier notifier = new RecordingNotifier();

    private CreateWorld createWorld(MapConfig config) {
        WorldsSettings settings = new WorldsSettings(config);
        return new CreateWorld(
                repository,
                engine,
                notifier.notifier(),
                new RecordingEvents(),
                new InlineScheduler(),
                CLOCK,
                settings::creationDefaults);
    }

    private static MapConfig everyDefaultChanged() {
        return new MapConfig()
                .set("defaults.environment", "NETHER")
                .set("defaults.world-type", "FLAT")
                .set("defaults.generate-structures", false)
                .set("settings.defaults.pvp", false)
                .set("settings.defaults.difficulty", "HARD")
                .set("settings.defaults.force-gamemode", "ADVENTURE")
                .set("settings.defaults.spawn-animals", false)
                .set("settings.defaults.spawn-monsters", false);
    }

    @Test
    void aWorldWithNothingChosenIsBuiltAndSavedWithTheConfiguredDefaults() {
        assertThat(createWorld(everyDefaultChanged())
                        .create(ALEX, GAMES, WorldCreateOptions.none(), true)
                        .isOk())
                .isTrue();

        WorldSpec built = engine.createdAs(GAMES).map(ManagedWorld::spec).orElseThrow();
        assertThat(built.environment()).isEqualTo(WorldEnvironment.NETHER);
        assertThat(built.worldType()).isEqualTo(WorldGenType.FLAT);
        assertThat(built.generateStructures()).isFalse();

        WorldSettings saved = repository.find(GAMES).map(ManagedWorld::settings).orElseThrow();
        assertThat(saved.get(WorldProperties.PVP)).isFalse();
        assertThat(saved.get(WorldProperties.DIFFICULTY)).isEqualTo(WorldDifficulty.HARD);
        assertThat(saved.get(WorldProperties.FORCE_GAMEMODE)).isEqualTo(ForcedGameMode.ADVENTURE);
        assertThat(saved.get(WorldProperties.SPAWN_ANIMALS)).isFalse();
        assertThat(saved.get(WorldProperties.SPAWN_MONSTERS)).isFalse();
    }

    @Test
    void whatTheOperatorChoseWinsOverTheDefaults() {
        WorldCreateOptions endWorld = new WorldCreateOptions(
                Optional.of(WorldEnvironment.THE_END), Optional.empty(), Optional.empty(), Optional.empty());

        createWorld(everyDefaultChanged()).create(ALEX, GAMES, endWorld, true);

        WorldSpec built = engine.createdAs(GAMES).map(ManagedWorld::spec).orElseThrow();
        assertThat(built.environment()).isEqualTo(WorldEnvironment.THE_END);
        assertThat(built.worldType()).as("the type was not chosen").isEqualTo(WorldGenType.FLAT);
    }

    @Test
    void aValueTheServerDoesNotKnowFallsBackToTheVanillaDefault() {
        MapConfig typos =
                new MapConfig().set("defaults.environment", "MOON").set("settings.defaults.difficulty", "EXTREME");

        createWorld(typos).create(ALEX, GAMES, WorldCreateOptions.none(), true);

        assertThat(engine.createdAs(GAMES).map(world -> world.spec().environment()))
                .contains(WorldEnvironment.NORMAL);
        assertThat(repository.find(GAMES).map(world -> world.settings().get(WorldProperties.DIFFICULTY)))
                .contains(WorldDifficulty.NORMAL);
    }
}
