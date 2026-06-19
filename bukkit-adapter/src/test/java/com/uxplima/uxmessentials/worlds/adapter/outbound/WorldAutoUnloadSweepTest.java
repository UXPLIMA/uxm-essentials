package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.event.WorldUnloaded;
import org.junit.jupiter.api.Test;

class WorldAutoUnloadSweepTest {

    private static final WorldName ARENA = WorldName.of("arena");
    private static final WorldName HUB = WorldName.of("hub");
    private static final WorldName KEEP = WorldName.of("keep");

    @Test
    void seedsThenUnloadsAnEmptyWorldOncePastIdle() {
        Fixture f = new Fixture();
        f.engine.setLoaded(ARENA);
        f.engine.setPlayers(ARENA, 0);
        Runnable tick = f.start();

        tick.run(); // first sweep only seeds lastNonEmpty for the empty world
        assertThat(f.engine.unloads).isEmpty();
        assertThat(f.events.published).isEmpty();

        f.clock.advance(Duration.ofMinutes(31));
        tick.run(); // idle past the 30m threshold now

        assertThat(f.engine.unloads).containsExactly(new Unload(ARENA, true));
        assertThat(f.events.published).containsExactly(new WorldUnloaded(ARENA));
    }

    @Test
    void neverUnloadsAnOccupiedWorld() {
        Fixture f = new Fixture();
        f.engine.setLoaded(ARENA);
        f.engine.setPlayers(ARENA, 2);
        Runnable tick = f.start();

        tick.run();
        f.clock.advance(Duration.ofHours(5));
        tick.run();
        f.clock.advance(Duration.ofHours(5));
        tick.run();

        assertThat(f.engine.unloads).isEmpty();
        assertThat(f.events.published).isEmpty();
    }

    @Test
    void neverUnloadsTheProtectedDefaultWorld() {
        Fixture f = new Fixture();
        f.engine.setLoaded(HUB);
        f.engine.setPlayers(HUB, 0);
        f.engine.setDefault(HUB);
        Runnable tick = f.start();

        tick.run();
        f.clock.advance(Duration.ofMinutes(45));
        tick.run();

        assertThat(f.engine.unloads).isEmpty();
        assertThat(f.events.published).isEmpty();
    }

    @Test
    void neverUnloadsAnExcludedWorld() {
        Fixture f = new Fixture();
        f.engine.setLoaded(KEEP);
        f.engine.setPlayers(KEEP, 0);
        Runnable tick = f.start();

        tick.run();
        f.clock.advance(Duration.ofMinutes(45));
        tick.run();

        assertThat(f.engine.unloads).isEmpty();
        assertThat(f.events.published).isEmpty();
    }

    @Test
    void disabledSweepSchedulesNothing() {
        FakeScheduler scheduler = new FakeScheduler();
        WorldsSettings settings = new WorldsSettings(config(false));
        WorldAutoUnloadSweep sweep = new WorldAutoUnloadSweep(
                scheduler, new FakeWorldEngine(), new CapturingPublisher(), settings, new NoOpLogger(), fixedClock());

        AutoCloseable handle = sweep.start();

        assertThat(scheduler.captured).isNull();
        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(handle::close);
    }

    @Test
    void prunesAWorldThatDisappearsBetweenTicks() {
        Fixture f = new Fixture();
        f.engine.setLoaded(ARENA);
        f.engine.setPlayers(ARENA, 0);
        Runnable tick = f.start();

        tick.run(); // seeds ARENA at t0

        // ARENA is unloaded by some other path and gone from the loaded set; the long-idle window must not
        // resurrect its old t0 seed and unload anything when it later reappears empty.
        f.engine.clearLoaded();
        f.clock.advance(Duration.ofHours(2));
        tick.run(); // prunes ARENA's stale entry

        f.engine.setLoaded(ARENA);
        f.engine.setPlayers(ARENA, 0);
        tick.run(); // re-seeds ARENA at the now-current time, so it is not yet idle

        assertThat(f.engine.unloads).isEmpty();
        assertThat(f.events.published).isEmpty();
    }

    /** Settings over an in-memory config: enabled, 30-minute idle, 60s interval, excluded {"keep"}. */
    private static ConfigStore config(boolean enabled) {
        return new ConfigStore() {
            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return path.endsWith("auto-unload.enabled") ? enabled : fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String path, int fallback) {
                if (path.endsWith("auto-unload.idle-minutes")) {
                    return 30;
                }
                if (path.endsWith("auto-unload.sweep-interval-seconds")) {
                    return 60;
                }
                return fallback;
            }

            @Override
            public List<String> getStringList(String path, List<String> fallback) {
                return path.endsWith("auto-unload.excluded-worlds") ? List.of("keep") : List.copyOf(fallback);
            }
        };
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    /** Bundles the enabled sweep with its doubles and exposes the captured tick. */
    private static final class Fixture {
        final FakeScheduler scheduler = new FakeScheduler();
        final FakeWorldEngine engine = new FakeWorldEngine();
        final CapturingPublisher events = new CapturingPublisher();
        final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        final WorldAutoUnloadSweep sweep = new WorldAutoUnloadSweep(
                scheduler, engine, events, new WorldsSettings(config(true)), new NoOpLogger(), clock);

        Runnable start() {
            sweep.start();
            return Objects.requireNonNull(scheduler.captured, "the enabled sweep must schedule a repeating tick");
        }
    }

    /** Captures the {@code repeatGlobal} task and hands back a recording closeable. */
    private static final class FakeScheduler implements Scheduler {

        @org.jspecify.annotations.Nullable Runnable captured;

        final AtomicInteger closes = new AtomicInteger();

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            this.captured = task;
            return closes::incrementAndGet;
        }

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }

    /** A settable engine recording every unload, with a configurable unload outcome. */
    private static final class FakeWorldEngine implements WorldEngine {
        private final Set<WorldName> loaded = new HashSet<>();
        private final Map<WorldName, Integer> players = new HashMap<>();
        private Optional<WorldName> defaultWorld = Optional.empty();
        private Result<Unit, WorldError> unloadResult = Result.ok();
        final List<Unload> unloads = new ArrayList<>();

        void setLoaded(WorldName name) {
            loaded.add(name);
        }

        void clearLoaded() {
            loaded.clear();
        }

        void setPlayers(WorldName name, int count) {
            players.put(name, count);
        }

        void setDefault(WorldName name) {
            this.defaultWorld = Optional.of(name);
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return Set.copyOf(loaded);
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return defaultWorld;
        }

        @Override
        public int playerCount(WorldName name) {
            return players.getOrDefault(name, 0);
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            unloads.add(new Unload(name, save));
            return unloadResult;
        }

        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
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
            return loaded.contains(name);
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return loaded.contains(name);
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.empty();
        }
    }

    private static final class CapturingPublisher implements DomainEventPublisher {
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    /** A test clock whose instant is advanced explicitly between sweeps. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            this.now = this.now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private static final class NoOpLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private record Unload(WorldName name, boolean save) {}
}
