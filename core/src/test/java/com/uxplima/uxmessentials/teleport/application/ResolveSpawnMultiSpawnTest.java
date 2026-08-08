package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the multi-spawn resolution model the operator commands {@code /setmainspawn}, {@code /removespawn}
 * and {@code /mirrorspawn} stand on. {@code /spawn} resolves a world's own spawn first, then the global main
 * spawn, then (last) whatever the directory's {@code defaultSpawn} folds in as a final fallback; a mirror
 * still wins over all of it. The use-case writers record into a fake directory so the resolution order, the
 * remove fall-through, and the mirror validation are checked without a server.
 */
class ResolveSpawnMultiSpawnTest {

    private static final WorldRef OVERWORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final WorldRef NETHER = new WorldRef(UUID.randomUUID(), "world_nether");

    private FakeSpawnDirectory spawns;
    private FakeWorldLookup worlds;
    private RecordingNotifier notifier;
    private ResolveSpawn resolveSpawn;
    private PlayerRef who;

    @BeforeEach
    void setUp() {
        spawns = new FakeSpawnDirectory();
        worlds = new FakeWorldLookup();
        worlds.add(OVERWORLD);
        worlds.add(NETHER);
        notifier = new RecordingNotifier();
        resolveSpawn = new ResolveSpawn(spawns, worlds, dummyEngine(), notifier.notifier());
        who = new PlayerRef(UUID.randomUUID(), "Steve");
    }

    @Test
    void aWorldsOwnSpawnWinsOverMain() {
        spawns.setMainSpawn(at(NETHER, 0, 64, 0));
        spawns.setDefaultSpawn(OVERWORLD, at(OVERWORLD, 10, 70, 10));

        assertThat(resolveSpawn.resolveDefault(OVERWORLD)).contains(at(OVERWORLD, 10, 70, 10));
    }

    @Test
    void aWorldWithoutItsOwnSpawnFallsBackToMain() {
        spawns.setMainSpawn(at(OVERWORLD, 5, 65, 5));

        assertThat(resolveSpawn.resolveDefault(NETHER)).contains(at(OVERWORLD, 5, 65, 5));
    }

    @Test
    void removingAWorldSpawnDropsItToMain() {
        spawns.setDefaultSpawn(NETHER, at(NETHER, 1, 1, 1));
        spawns.setMainSpawn(at(OVERWORLD, 5, 65, 5));

        resolveSpawn.removeWorldSpawn(who, NETHER);

        assertThat(resolveSpawn.resolveDefault(NETHER)).contains(at(OVERWORLD, 5, 65, 5));
        assertThat(notifier.lastKey).isEqualTo(TeleportMessageKey.SPAWN_REMOVED);
    }

    @Test
    void removingAWorldSpawnThatWasNotSetReportsNone() {
        resolveSpawn.removeWorldSpawn(who, NETHER);

        assertThat(notifier.lastKey).isEqualTo(TeleportMessageKey.SPAWN_REMOVE_NONE);
    }

    @Test
    void setMainRoundTrips() {
        resolveSpawn.setMain(who, at(OVERWORLD, 8, 66, 8));

        assertThat(spawns.mainSpawn()).contains(at(OVERWORLD, 8, 66, 8));
        assertThat(notifier.lastKey).isEqualTo(TeleportMessageKey.SPAWN_MAIN_SET);
    }

    @Test
    void aMirrorStillWinsOverEverything() {
        spawns.setDefaultSpawn(OVERWORLD, at(OVERWORLD, 99, 99, 99));
        spawns.setDefaultSpawn(NETHER, at(NETHER, 1, 64, 1));
        spawns.setMirror(new SpawnMirror(OVERWORLD.uid(), NETHER.uid()));

        assertThat(resolveSpawn.resolveDefault(OVERWORLD)).contains(at(NETHER, 1, 64, 1));
    }

    @Test
    void mirrorWritesTheRedirectAndNotifies() {
        resolveSpawn.mirror(who, OVERWORLD, "world_nether");

        assertThat(spawns.mirrorFor(OVERWORLD)).isPresent();
        assertThat(spawns.mirrorFor(OVERWORLD).orElseThrow().targetWorld()).isEqualTo(NETHER.uid());
        assertThat(notifier.lastKey).isEqualTo(TeleportMessageKey.SPAWN_MIRRORED);
    }

    @Test
    void mirrorToAnUnknownWorldIsRejected() {
        resolveSpawn.mirror(who, OVERWORLD, "the_end");

        assertThat(spawns.mirrorFor(OVERWORLD)).isEmpty();
        assertThat(notifier.lastKey).isEqualTo(TeleportMessageKey.SPAWN_MIRROR_UNKNOWN_WORLD);
    }

    @Test
    void mirrorToTheSameWorldIsRejected() {
        resolveSpawn.mirror(who, OVERWORLD, "world");

        assertThat(spawns.mirrorFor(OVERWORLD)).isEmpty();
        assertThat(notifier.lastKey).isEqualTo(TeleportMessageKey.SPAWN_MIRROR_SELF);
    }

    private static Position at(WorldRef world, double x, double y, double z) {
        return new Position(world, x, y, z, 0f, 0f);
    }

    /** An in-memory directory matching the no-vanilla-fallback contract the jOOQ store also honours. */
    private static final class FakeSpawnDirectory implements SpawnDirectory {
        private final Map<UUID, Position> perWorld = new HashMap<>();
        private final Map<String, Position> named = new HashMap<>();
        private final Map<UUID, SpawnMirror> mirrors = new HashMap<>();
        private Optional<Position> main = Optional.empty();

        @Override
        public Optional<Position> defaultSpawn(WorldRef world) {
            return operatorSpawn(world);
        }

        @Override
        public Optional<Position> operatorSpawn(WorldRef world) {
            return Optional.ofNullable(perWorld.get(world.uid()));
        }

        @Override
        public Optional<Position> mainSpawn() {
            return main;
        }

        @Override
        public Optional<Position> namedSpawn(String name) {
            return Optional.ofNullable(named.get(name));
        }

        @Override
        public Optional<SpawnMirror> mirrorFor(WorldRef world) {
            return Optional.ofNullable(mirrors.get(world.uid()));
        }

        @Override
        public void setDefaultSpawn(WorldRef world, Position position) {
            perWorld.put(world.uid(), position);
        }

        @Override
        public void setNamedSpawn(String name, Position position) {
            named.put(name, position);
        }

        @Override
        public void setMainSpawn(Position position) {
            main = Optional.of(position);
        }

        @Override
        public boolean removeDefaultSpawn(WorldRef world) {
            return perWorld.remove(world.uid()) != null;
        }

        @Override
        public void setMirror(SpawnMirror mirror) {
            mirrors.put(mirror.sourceWorld(), mirror);
        }
    }

    private static final class FakeWorldLookup implements WorldLookup {
        private final List<WorldRef> known = new ArrayList<>();

        void add(WorldRef world) {
            known.add(world);
        }

        @Override
        public Optional<WorldRef> findByName(String name) {
            return known.stream().filter(w -> w.name().equals(name)).findFirst();
        }

        @Override
        public Optional<WorldRef> findByUid(UUID uid) {
            return known.stream().filter(w -> w.uid().equals(uid)).findFirst();
        }
    }

    /**
     * A {@link TeleportEngine} whose collaborators are no-ops. The multi-spawn writers and {@code
     * resolveDefault} never call {@code launch}, so the engine only has to be a valid non-null instance.
     */
    private static TeleportEngine dummyEngine() {
        Cooldowns cooldowns = new Cooldowns() {
            @Override
            public com.uxplima.uxmessentials.shared.domain.Result<
                            com.uxplima.uxmessentials.shared.domain.Unit, java.time.Duration>
                    check(PlayerRef who, Cooldowns.CooldownKind kind) {
                return com.uxplima.uxmessentials.shared.domain.Result.ok();
            }

            @Override
            public void stamp(PlayerRef who, Cooldowns.CooldownKind kind) {}

            @Override
            public com.uxplima.uxmessentials.shared.domain.Result<
                            com.uxplima.uxmessentials.shared.domain.Unit, java.time.Duration>
                    checkLabel(PlayerRef who, String label) {
                return com.uxplima.uxmessentials.shared.domain.Result.ok();
            }

            @Override
            public void stampLabel(PlayerRef who, String label) {}
        };
        Warmups warmups = (who, kind, onComplete, onCancel) -> new Warmups.CompletedWarmup(who);
        Messages messages = (viewer, key, placeholders) -> "";
        MessageSink sink = (viewer, rendered) -> {};
        Notifier engineNotifier = new Notifier(messages, sink);
        return new TeleportEngine(
                cooldowns,
                warmups,
                (mover, destination, kind) -> {},
                engineNotifier,
                event -> {},
                new TeleportSettings(defaults()),
                JailGate.NEVER);
    }

    private static com.uxplima.uxmessentials.shared.application.port.ConfigStore defaults() {
        return new com.uxplima.uxmessentials.shared.application.port.ConfigStore() {
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
        };
    }

    private static final class RecordingNotifier {
        private @Nullable MessageKey lastKey;

        Notifier notifier() {
            Messages messages = (viewer, key, placeholders) -> {
                lastKey = key;
                return "";
            };
            MessageSink sink = (viewer, rendered) -> {};
            return new Notifier(messages, sink);
        }
    }
}
