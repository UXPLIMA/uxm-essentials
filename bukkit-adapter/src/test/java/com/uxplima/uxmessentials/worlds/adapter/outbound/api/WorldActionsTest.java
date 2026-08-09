package com.uxplima.uxmessentials.worlds.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published world actions: a registered world loads, a world that is already in the state asked for says so
 * rather than doing it twice, and the two refusals that protect a running server hold.
 */
class WorldActionsTest {

    private static final Instant T0 = Instant.parse("2026-08-09T12:00:00Z");
    private static final WorldName ARENA = WorldName.of("event_arena");

    private FakeRepository repository;
    private FakeEngine engine;
    private ActionDoubles.InlineScheduler scheduler;
    private boolean protectDefault;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        engine = new FakeEngine();
        scheduler = new ActionDoubles.InlineScheduler();
        protectDefault = true;
        repository.register(ARENA);
    }

    @Test
    void loadingARegisteredWorldLoadsIt() {
        UxmOutcome outcome = actions().load("event_arena").join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(engine.loaded).contains("event_arena");
    }

    @Test
    void loadingAWorldNobodyRegisteredIsAFailureRatherThanAnException() {
        UxmOutcome outcome = actions().load("nowhere").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.NOT_FOUND)).isTrue();
    }

    @Test
    void loadingAWorldThatIsAlreadyLoadedSaysSo() {
        engine.loaded.add("event_arena");

        UxmOutcome outcome = actions().load("event_arena").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.ALREADY_IN_STATE)).isTrue();
    }

    @Test
    void unloadingTakesTheWorldBackOut() {
        engine.loaded.add("event_arena");

        UxmOutcome outcome = actions().unload("event_arena").join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(engine.loaded).doesNotContain("event_arena");
        assertThat(engine.savedOnUnload).containsExactly(true);
    }

    @Test
    void unloadingWithoutSavingThrowsAwayWhatChanged() {
        engine.loaded.add("event_arena");

        actions().unload("event_arena", false).join();

        assertThat(engine.savedOnUnload).containsExactly(false);
    }

    @Test
    void aWorldWithPlayersStillInsideIsNotUnloadedFromUnderThem() {
        engine.loaded.add("event_arena");
        engine.playerCount = 3;

        UxmOutcome outcome = actions().unload("event_arena").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.REFUSED)).isTrue();
        assertThat(engine.loaded).contains("event_arena");
    }

    @Test
    void theDefaultWorldIsNotUnloadableWhileItIsProtected() {
        repository.register(WorldName.of("world"));
        engine.loaded.add("world");

        UxmOutcome outcome = actions().unload("world").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.REFUSED)).isTrue();
    }

    @Test
    void aNameThatCouldLeaveTheWorldsFolderIsRefusedWhereTheCallerStands() {
        assertThatThrownBy(() -> actions().load("../secrets")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theWorkRunsOnTheServersOwnThread() {
        engine.loaded.add("event_arena");

        actions().unload("event_arena").join();

        assertThat(scheduler.globalCalls()).isEqualTo(1);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    private WorldActions actions() {
        LoadWorld load = new LoadWorld(
                repository, engine, ActionDoubles.silentNotifier(), new ActionDoubles.RecordingEvents(), scheduler);
        UnloadWorld unload = new UnloadWorld(
                engine, ActionDoubles.silentNotifier(), new ActionDoubles.RecordingEvents(), () -> protectDefault);
        return new WorldActions(new WorldApiWrites(load, unload), scheduler, "TestPlugin");
    }

    /** Holds the registered worlds, which is all the load path reads and all the unload path ignores. */
    private static final class FakeRepository implements WorldRepository {

        private final Map<String, ManagedWorld> known = new HashMap<>();

        void register(WorldName name) {
            known.put(name.value(), ManagedWorld.created(name, WorldSpec.normal(), true, Optional.empty(), T0));
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(known.get(name.value()));
        }

        @Override
        public List<ManagedWorld> all() {
            return List.copyOf(known.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return known.containsKey(name.value());
        }

        @Override
        public void save(ManagedWorld world) {
            known.put(world.name().value(), world);
        }

        @Override
        public void delete(WorldName name) {
            known.remove(name.value());
        }
    }

    /** Remembers which worlds are up, and what each unload was asked to do with the region files. */
    private static final class FakeEngine implements WorldEngine {

        private final Set<String> loaded = new HashSet<>();
        private final List<Boolean> savedOnUnload = new ArrayList<>();
        private int playerCount;

        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            loaded.add(world.name().value());
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
            loaded.add(world.name().value());
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            savedOnUnload.add(save);
            loaded.remove(name.value());
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> deleteFiles(WorldName name) {
            return Result.ok();
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            return true;
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return loaded.contains(name.value());
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return loaded.stream().map(WorldName::of).collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return Optional.of(WorldName.of("world"));
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public int playerCount(WorldName name) {
            return playerCount;
        }

        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.empty();
        }
    }
}
