package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.LocalServer;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.Fixtures;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The main spawn each server falls back to: its own, then its set's, then the network's. */
class ResolveSpawnTest {

    private static final Position NETWORK = Fixtures.at(0, 64, 0);
    private static final Position LOBBY_SET = Fixtures.at(100, 64, 0);
    private static final Position LOBBY_1 = Fixtures.at(200, 64, 0);

    private final Spawns spawns = new Spawns();
    private final RecordingNotifier notifier = new RecordingNotifier();

    private ResolveSpawn on(LocalServer local, @Nullable ServerScope defaultScope) {
        TeleportEngine engine = new TeleportEngine(
                new ClockCooldowns(CLOCK),
                (who, kind, onComplete, onCancel) -> {
                    throw new AssertionError("resolving a spawn starts no warmup");
                },
                (who, destination, kind) -> {
                    throw new AssertionError("resolving a spawn moves nobody");
                },
                notifier.notifier(),
                new RecordingEvents(),
                new TeleportSettings(new Defaults()),
                who -> false);
        return new ResolveSpawn(spawns, new NoWorlds(), engine, notifier.notifier(), local, defaultScope);
    }

    private static LocalServer server(String id, String... sets) {
        return new LocalServer(id, List.of(sets));
    }

    @Test
    void everyServerFallsBackToTheNetworkSpawnWhenNothingIsScoped() {
        spawns.setMainSpawn(null, NETWORK);

        assertThat(on(server("lobby-1", "lobby"), null).resolveDefault(WORLD)).contains(NETWORK);
        assertThat(on(server("survival"), null).resolveDefault(WORLD)).contains(NETWORK);
    }

    @Test
    void aServerUsesItsOwnSpawnBeforeItsSetsAndTheNetworks() {
        spawns.setMainSpawn(null, NETWORK);
        spawns.setMainSpawn(ServerScope.set("lobby"), LOBBY_SET);
        spawns.setMainSpawn(ServerScope.server("lobby-1"), LOBBY_1);

        assertThat(on(server("lobby-1", "lobby"), null).resolveMain()).contains(LOBBY_1);
        assertThat(on(server("lobby-2", "lobby"), null).resolveMain()).contains(LOBBY_SET);
        assertThat(on(server("survival"), null).resolveMain()).contains(NETWORK);
    }

    @Test
    void aServerWithNoSpawnOfItsOwnAndNoNetworkSpawnHasNone() {
        spawns.setMainSpawn(ServerScope.server("lobby-1"), LOBBY_1);

        assertThat(on(server("survival"), null).resolveMain()).isEmpty();
    }

    @Test
    void aPlainSetMainSpawnWritesThisServersScopeAndLeavesTheOthersAlone() {
        spawns.setMainSpawn(null, NETWORK);
        ResolveSpawn lobby = on(server("lobby-1", "lobby"), ServerScope.server("lobby-1"));

        lobby.setMain(ALEX, LOBBY_1);

        assertThat(spawns.mainSpawn(ServerScope.server("lobby-1"))).contains(LOBBY_1);
        assertThat(on(server("survival"), null).resolveMain()).contains(NETWORK);
        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(
                        ALEX, "teleport.spawn.main-set-scoped", Map.of("scope", "server:lobby-1")));
    }

    @Test
    void theNetworkScopeWritesTheSpawnEveryServerFallsBackTo() {
        ResolveSpawn lobby = on(server("lobby-1"), ServerScope.server("lobby-1"));

        assertThat(lobby.setMain(ALEX, "network", NETWORK)).isTrue();

        assertThat(on(server("survival"), null).resolveMain()).contains(NETWORK);
        assertThat(notifier.keys()).containsExactly("teleport.spawn.main-set");
    }

    @Test
    void aScopeWordThatNamesNoScopeWritesNothing() {
        ResolveSpawn lobby = on(server("lobby-1"), ServerScope.server("lobby-1"));

        assertThat(lobby.setMain(ALEX, "minigames", LOBBY_1)).isFalse();

        assertThat(spawns.mains).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.spawn.scope-invalid");
    }

    /** Main spawns by scope in memory; the network-wide one under the empty key. */
    private static final class Spawns implements SpawnDirectory {
        private final Map<String, Position> mains = new HashMap<>();

        private static String key(@Nullable ServerScope scope) {
            return scope == null ? "" : scope.value();
        }

        @Override
        public Optional<Position> defaultSpawn(WorldRef world) {
            return Optional.empty();
        }

        @Override
        public Optional<Position> operatorSpawn(WorldRef world) {
            return Optional.empty();
        }

        @Override
        public Optional<Position> mainSpawn(@Nullable ServerScope scope) {
            return Optional.ofNullable(mains.get(key(scope)));
        }

        @Override
        public Optional<Position> namedSpawn(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<SpawnMirror> mirrorFor(WorldRef world) {
            return Optional.empty();
        }

        @Override
        public void setDefaultSpawn(WorldRef world, Position position) {}

        @Override
        public void setNamedSpawn(String name, Position position) {}

        @Override
        public void setMainSpawn(@Nullable ServerScope scope, Position position) {
            mains.put(key(scope), position);
        }

        @Override
        public boolean removeDefaultSpawn(WorldRef world) {
            return false;
        }

        @Override
        public void setMirror(SpawnMirror mirror) {}
    }

    private static final class NoWorlds implements WorldLookup {
        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<WorldRef> findByUid(UUID uid) {
            return Optional.empty();
        }
    }

    /** Teleport settings at their defaults. */
    private static final class Defaults implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
