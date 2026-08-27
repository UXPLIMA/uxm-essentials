package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.application.port.RescueTargets;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

class ResolveVoidRescueTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final Map<String, Position> spawns = new HashMap<>();
    private final Map<String, Position> warps = new HashMap<>();
    private final Map<String, WorldRef> loaded = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final MovableClock clock = new MovableClock(Instant.EPOCH);

    private final WorldRef lobby = world("lobby");
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Faller");

    private final ResolveVoidRescue resolve = new ResolveVoidRescue(
            repo,
            new RescueTargets() {
                @Override
                public Optional<Position> spawn(WorldRef world) {
                    return Optional.ofNullable(spawns.get(world.name()));
                }

                @Override
                public Optional<Position> warp(String name) {
                    return Optional.ofNullable(warps.get(name));
                }
            },
            new WorldLookup() {
                @Override
                public Optional<WorldRef> findByName(String name) {
                    return Optional.ofNullable(loaded.get(name));
                }

                @Override
                public Optional<WorldRef> findByUid(UUID uid) {
                    return loaded.values().stream()
                            .filter(ref -> ref.uid().equals(uid))
                            .findFirst();
                }
            },
            new CapturingLogger(),
            clock);

    private WorldRef world(String name) {
        WorldRef ref = new WorldRef(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
        loaded.put(name, ref);
        return ref;
    }

    private void manage(String name, String chain) {
        ManagedWorld managed =
                ManagedWorld.created(WorldName.of(name), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH);
        repo.save(
                chain.isEmpty()
                        ? managed
                        : managed.withSettings(managed.settings().withRaw("void-rescue", chain)));
    }

    @Test
    void anUnmanagedOrUnconfiguredWorldIsNotArmed() {
        assertThat(resolve.armed(lobby)).isFalse();
        manage("lobby", "");
        assertThat(resolve.armed(lobby)).isFalse();
        assertThat(resolve.rescue(who, lobby)).isEmpty();
    }

    @Test
    void spawnIsResolvedThroughThePort() {
        manage("lobby", "spawn");
        spawns.put("lobby", Position.of(lobby, 0, 80, 0));

        assertThat(resolve.armed(lobby)).isTrue();
        assertThat(resolve.rescue(who, lobby)).contains(Position.of(lobby, 0, 80, 0));
    }

    @Test
    void theFirstResolvableStepWinsAndAnUnresolvedChainLeavesTheFallAlone() {
        WorldRef arena = world("arena");
        manage("arena", "warp:missing;at:lobby,10,64,-4;spawn");
        spawns.put("arena", Position.of(arena, 1, 1, 1));

        assertThat(resolve.rescue(who, arena)).contains(Position.of(lobby, 10, 64, -4));

        loaded.remove("lobby");
        assertThat(resolve.rescue(who, arena)).contains(Position.of(arena, 1, 1, 1));

        spawns.clear();
        assertThat(resolve.rescue(who, arena)).isEmpty();
    }

    @Test
    void aWarpStepUsesTheWarpsPort() {
        manage("lobby", "warp:Hub");
        warps.put("Hub", Position.of(lobby, 5, 70, 5));

        assertThat(resolve.rescue(who, lobby)).contains(Position.of(lobby, 5, 70, 5));
    }

    @Test
    void anAtStepIsSkippedWhileItsWorldIsNotLoaded() {
        manage("lobby", "at:nowhere,0,64,0");

        assertThat(resolve.armed(lobby)).isTrue();
        assertThat(resolve.rescue(who, lobby)).isEmpty();
    }

    @Test
    void theTriggerHeightIsOnlyReportedForAnArmedWorld() {
        manage("lobby", "");
        assertThat(resolve.triggerY(lobby)).isEmpty();

        manage("lobby", "spawn");
        assertThat(resolve.triggerY(lobby)).isEmpty();

        ManagedWorld managed = repo.find(WorldName.of("lobby")).orElseThrow();
        repo.save(managed.withSettings(managed.settings().with(WorldProperties.VOID_RESCUE_Y, Optional.of(-20))));

        assertThat(resolve.triggerY(lobby)).hasValue(-20);
    }

    @Test
    void aRescueLoopIsDisarmedAfterThreeRescuesInTheWindow() {
        manage("lobby", "spawn");
        spawns.put("lobby", Position.of(lobby, 0, -100, 0));

        assertThat(resolve.rescue(who, lobby)).isPresent();
        assertThat(resolve.rescue(who, lobby)).isPresent();
        assertThat(resolve.rescue(who, lobby)).isPresent();
        assertThat(resolve.rescue(who, lobby)).isEmpty();
        assertThat(resolve.rescue(who, lobby)).isEmpty();
        assertThat(warnings).hasSize(1);

        clock.advance(Duration.ofSeconds(ResolveVoidRescue.WINDOW_SECONDS));
        assertThat(resolve.rescue(who, lobby)).isPresent();
    }

    @Test
    void leavingForgetsTheBurst() {
        manage("lobby", "spawn");
        spawns.put("lobby", Position.of(lobby, 0, -100, 0));

        for (int i = 0; i < ResolveVoidRescue.MAX_RESCUES; i++) {
            assertThat(resolve.rescue(who, lobby)).isPresent();
        }
        resolve.forget(who);

        assertThat(resolve.rescue(who, lobby)).isPresent();
    }

    private final class CapturingLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
