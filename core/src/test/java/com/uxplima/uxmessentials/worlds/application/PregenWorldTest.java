package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldPregen;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/**
 * {@code PregenWorld} is the thin, pure front for the asynchronous pre-generation engine: it validates the
 * request and reports the immediate command outcome, delegating the actual generation to the {@link WorldPregen}
 * port. The truth table covered here: an unknown world never delegates and reports {@link WorldError#NOT_FOUND};
 * an unloaded world is refused before delegating; a non-positive radius is rejected as {@link
 * WorldError#SETTING_INVALID_VALUE} without delegating; an already-running pre-generation is refused without
 * re-delegating; the happy path clamps the radius to {@code pregenMaxRadius}, delegates the clamped radius,
 * notifies {@code WORLD_PREGEN_STARTED} with the clamped chunk count, and returns the port's own result;
 * cancelling a running pre-generation reports {@code CANCELLED}; cancelling an idle one reports {@code
 * PREGEN_NOT_RUNNING}. The engine, repository, settings, notifier and scheduler are all in-memory fakes so
 * the orchestration is asserted without Bukkit. The engine itself owns the asynchronous completion ("done")
 * notification, so it is deliberately never asserted here.
 */
class PregenWorldTest {

    private static final PlayerRef PLAYER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldName WORLD = WorldName.of("mine");

    private final FakeWorldRepository repository = new FakeWorldRepository();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final FakeWorldPregen pregen = new FakeWorldPregen();
    private final CapturingNotifier notifier = new CapturingNotifier();

    private PregenWorld service(int maxRadius) {
        WorldsSettings settings = new WorldsSettings(new FixedConfig(Map.of("pregen.max-radius", maxRadius)));
        return new PregenWorld(
                repository, engine, pregen, settings, notifier.notifier(), TestSupport.inlineScheduler());
    }

    private void registerWorld() {
        repository.save(ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
    }

    @Test
    void notFoundWhenWorldIsUnknownNeverDelegates() {
        var result = service(200).start(PLAYER, WORLD, 10);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.NOT_FOUND);
        assertThat(pregen.starts).isEmpty();
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_NOT_FOUND);
    }

    @Test
    void notLoadedWhenWorldIsRegisteredButNotLoadedNeverDelegates() {
        registerWorld();

        var result = service(200).start(PLAYER, WORLD, 10);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.NOT_LOADED);
        assertThat(pregen.starts).isEmpty();
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_NOT_LOADED);
    }

    @Test
    void nonPositiveRadiusIsRejectedAsInvalidValueWithoutDelegating() {
        registerWorld();
        engine.loaded.add(WORLD.value());

        var result = service(200).start(PLAYER, WORLD, 0);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.SETTING_INVALID_VALUE);
        assertThat(pregen.starts).isEmpty();
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_SETTING_INVALID_VALUE);
    }

    @Test
    void alreadyRunningRefusesWithoutDelegating() {
        registerWorld();
        engine.loaded.add(WORLD.value());
        pregen.running = true;

        var result = service(200).start(PLAYER, WORLD, 10);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.PREGEN_ALREADY_RUNNING);
        assertThat(pregen.starts).isEmpty();
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_PREGEN_ALREADY_RUNNING);
    }

    @Test
    void happyPathClampsRadiusDelegatesAndNotifiesStartedWithChunkCount() {
        registerWorld();
        engine.loaded.add(WORLD.value());

        var result = service(200).start(PLAYER, WORLD, 9999);

        assertThat(result.isOk()).isTrue();
        assertThat(pregen.starts).hasSize(1);
        assertThat(pregen.starts.get(0).who()).isEqualTo(PLAYER);
        assertThat(pregen.starts.get(0).world()).isEqualTo(WORLD);
        assertThat(pregen.starts.get(0).radius()).isEqualTo(200);
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_PREGEN_STARTED);
        assertThat(notifier.last)
                .containsEntry("world", WORLD.value())
                .containsEntry("chunks", String.valueOf((2L * 200 + 1) * (2L * 200 + 1)));
    }

    @Test
    void happyPathReturnsThePortsOwnResult() {
        registerWorld();
        engine.loaded.add(WORLD.value());
        Result<Unit, WorldError> portResult = Result.ok();
        pregen.startResult = portResult;

        var result = service(200).start(PLAYER, WORLD, 5);

        assertThat(result).isSameAs(portResult);
    }

    @Test
    void cancelRunningReportsCancelled() {
        pregen.cancelResult = true;

        var result = service(200).cancel(PLAYER, WORLD);

        assertThat(result.isOk()).isTrue();
        assertThat(pregen.cancelled).containsExactly(WORLD);
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_PREGEN_CANCELLED);
    }

    @Test
    void cancelIdleReportsNotRunning() {
        pregen.cancelResult = false;

        var result = service(200).cancel(PLAYER, WORLD);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.PREGEN_NOT_RUNNING);
        assertThat(pregen.cancelled).containsExactly(WORLD);
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_PREGEN_NOT_RUNNING);
    }

    private record StartCall(PlayerRef who, WorldName world, int radius) {}

    /** Records every {@code start}/{@code cancel} call and answers from settable flags and a settable result. */
    private static final class FakeWorldPregen implements WorldPregen {
        final List<StartCall> starts = new ArrayList<>();
        final List<WorldName> cancelled = new ArrayList<>();
        boolean running;
        boolean cancelResult;
        Result<Unit, WorldError> startResult = Result.ok();

        @Override
        public Result<Unit, WorldError> start(PlayerRef initiator, WorldName world, int radius) {
            starts.add(new StartCall(initiator, world, radius));
            return startResult;
        }

        @Override
        public boolean cancel(WorldName world) {
            cancelled.add(world);
            return cancelResult;
        }

        @Override
        public boolean isRunning(WorldName world) {
            return running;
        }

        @Override
        public void stopAll() {
            throw new UnsupportedOperationException("the use case never stops all pre-generations");
        }
    }

    /** Captures the keys and last placeholder map the service emits, in order, through a real {@link WorldNotifier}. */
    private static final class CapturingNotifier {
        final List<MessageKey> keys = new ArrayList<>();
        Map<String, String> last = Map.of();

        WorldNotifier notifier() {
            MessageSink sink = (v, text) -> {};
            return new WorldNotifier(new RecordingMessages(this), sink);
        }
    }

    /** A {@link Messages} that records each resolved key and its placeholders, rendering the raw key string. */
    private record RecordingMessages(CapturingNotifier capture) implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            capture.keys.add(key);
            capture.last = placeholders;
            return key.key();
        }
    }

    /** A map-backed {@link ConfigStore} addressing keys by their dotted path relative to the module root. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }
}
