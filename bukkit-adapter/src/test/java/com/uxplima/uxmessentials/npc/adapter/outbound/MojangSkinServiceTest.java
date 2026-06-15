package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * Unit-tests the {@link MojangSkinService}'s two-step orchestration, gson parsing, fail-soft empties and
 * caching against a fake HTTP seam — no live Mojang call is ever made. The fake maps each URI to a canned body
 * (or no body for a 404), counts how many fetches it sees per URI, and a deferred scheduler runs the async work
 * synchronously so the returned future is already complete by the time the test reads it.
 */
class MojangSkinServiceTest {

    private static final String PROFILE_URI = "https://api.mojang.com/users/profiles/minecraft/notch";
    private static final String SESSION_URI =
            "https://sessionserver.mojang.com/session/minecraft/profile/069a79f444e94726a5befca90e38aaf5?unsigned=false";

    @Test
    void resolvesNameToUuidToSignedTexture() {
        FakeFetcher fetcher = new FakeFetcher();
        fetcher.bodies.put(PROFILE_URI, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        fetcher.bodies.put(
                SESSION_URI,
                "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"properties\":["
                        + "{\"name\":\"textures\",\"value\":\"dGV4dHVyZQ==\",\"signature\":\"sig=\"}]}");
        MojangSkinService service = newService(fetcher);

        Optional<NpcSkin> skin = await(service.fetchByName("Notch"));

        assertThat(skin).isPresent();
        assertThat(skin.get().texture()).isEqualTo("dGV4dHVyZQ==");
        assertThat(skin.get().signature()).isEqualTo("sig=");
    }

    @Test
    void unknownNameYieldsEmptyAndSkipsTheSecondCall() {
        FakeFetcher fetcher = new FakeFetcher();
        // No body for the profile URI models the 404/empty-body unknown-player response.
        MojangSkinService service = newService(fetcher);

        Optional<NpcSkin> skin = await(service.fetchByName("Notch"));

        assertThat(skin).isEmpty();
        assertThat(fetcher.calls.getOrDefault(SESSION_URI, 0)).isZero();
    }

    @Test
    void missingTexturesPropertyYieldsEmpty() {
        FakeFetcher fetcher = new FakeFetcher();
        fetcher.bodies.put(PROFILE_URI, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        fetcher.bodies.put(
                SESSION_URI,
                "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"properties\":["
                        + "{\"name\":\"cape\",\"value\":\"x\"}]}");
        MojangSkinService service = newService(fetcher);

        assertThat(await(service.fetchByName("Notch"))).isEmpty();
    }

    @Test
    void malformedJsonYieldsEmpty() {
        FakeFetcher fetcher = new FakeFetcher();
        fetcher.bodies.put(PROFILE_URI, "not json at all {");
        MojangSkinService service = newService(fetcher);

        assertThat(await(service.fetchByName("Notch"))).isEmpty();
    }

    @Test
    void blankUsernameYieldsEmptyWithoutAnyFetch() {
        FakeFetcher fetcher = new FakeFetcher();
        MojangSkinService service = newService(fetcher);

        assertThat(await(service.fetchByName("   "))).isEmpty();
        assertThat(fetcher.calls).isEmpty();
    }

    @Test
    void aUsernameWithIllegalUriCharactersCompletesEmptyWithoutHangingOrFetching() {
        FakeFetcher fetcher = new FakeFetcher();
        MojangSkinService service = newService(fetcher);

        // A space (or backslash/pipe/stray percent) cannot form a legal URI path segment; URI.create would
        // throw out of the async stage and leave the future uncompleted, so the service must reject it as a miss.
        CompletableFuture<Optional<NpcSkin>> future = service.fetchByName("not a name");

        assertThat(future).isCompleted();
        assertThat(future.join()).isEmpty();
        assertThat(fetcher.calls).isEmpty();
    }

    @Test
    void aSecondLookupForTheSameNameIsServedFromCache() {
        FakeFetcher fetcher = new FakeFetcher();
        fetcher.bodies.put(PROFILE_URI, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        fetcher.bodies.put(
                SESSION_URI,
                "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"properties\":["
                        + "{\"name\":\"textures\",\"value\":\"dGV4dHVyZQ==\",\"signature\":\"sig=\"}]}");
        MojangSkinService service = newService(fetcher);

        // Mixed-case repeats normalise to one cache key, so only the first lookup hits the network.
        assertThat(await(service.fetchByName("Notch"))).isPresent();
        assertThat(await(service.fetchByName("NOTCH"))).isPresent();

        assertThat(fetcher.calls.get(PROFILE_URI)).isEqualTo(1);
        assertThat(fetcher.calls.get(SESSION_URI)).isEqualTo(1);
    }

    @Test
    void anUnknownNameIsCachedSoItIsNotReFetched() {
        FakeFetcher fetcher = new FakeFetcher();
        MojangSkinService service = newService(fetcher);

        assertThat(await(service.fetchByName("Ghost"))).isEmpty();
        assertThat(await(service.fetchByName("Ghost"))).isEmpty();

        assertThat(fetcher.calls.values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(1);
    }

    private static MojangSkinService newService(FakeFetcher fetcher) {
        return new MojangSkinService(new ImmediateScheduler(), new NoOpLogger(), fetcher);
    }

    private static Optional<NpcSkin> await(CompletableFuture<Optional<NpcSkin>> future) {
        assertThat(future).isCompleted();
        return future.join();
    }

    /** A stubbed HTTP seam: each URI maps to a canned body, with an absent mapping standing in for a 404. */
    private static final class FakeFetcher implements HttpFetcher {
        private final Map<String, String> bodies = new HashMap<>();
        private final Map<String, Integer> calls = new HashMap<>();

        @Override
        public Optional<String> get(URI uri) {
            calls.merge(uri.toString(), 1, Integer::sum);
            return Optional.ofNullable(bodies.get(uri.toString()));
        }
    }

    /** Runs the scheduled async task on the calling thread so the future completes synchronously for the test. */
    private static final class ImmediateScheduler implements Scheduler {
        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }
    }

    private static final class NoOpLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();
        private final AtomicInteger errors = new AtomicInteger();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {
            errors.incrementAndGet();
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
