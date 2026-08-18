package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.npc.application.port.SkinService;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HttpFetcher;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.MineSkinService;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.MojangSkins;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * Pins that {@link CompositeSkinService} routes {@code fetchByName} to the Mojang service and
 * {@code fetchFromUrl} to the MineSkin service, presenting both as one {@link SkinService} port. Each leg is
 * driven through its own fake HTTP seam, so no live network is touched.
 */
class CompositeSkinServiceTest {

    private static final String PROFILE_URI = "https://api.mojang.com/users/profiles/minecraft/notch";
    private static final String SESSION_URI =
            "https://sessionserver.mojang.com/session/minecraft/profile/069a79f444e94726a5befca90e38aaf5?unsigned=false";

    @Test
    void fetchByNameGoesToTheMojangLeg() {
        FakeFetcher mojang = new FakeFetcher();
        mojang.bodies.put(PROFILE_URI, body -> "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        mojang.getResponse(
                SESSION_URI,
                "{\"properties\":[{\"name\":\"textures\",\"value\":\"dGV4dHVyZQ==\",\"signature\":\"sig=\"}]}");
        SkinService service = composite(mojang, new FakeFetcher());

        Optional<NpcSkin> skin = await(service.fetchByName("Notch"));

        assertThat(skin).contains(new NpcSkin("dGV4dHVyZQ==", "sig="));
    }

    @Test
    void fetchFromUrlGoesToTheMineSkinLeg() {
        FakeFetcher mineskin = new FakeFetcher();
        mineskin.postResponse = Optional.of("{\"data\":{\"texture\":{\"value\":\"Z2Vu\",\"signature\":\"gen=\"}}}");
        SkinService service = composite(new FakeFetcher(), mineskin);

        Optional<NpcSkin> skin = await(service.fetchFromUrl("https://example.com/skin.png"));

        assertThat(skin).contains(new NpcSkin("Z2Vu", "gen="));
        assertThat(mineskin.posts).isEqualTo(1);
    }

    private static SkinService composite(FakeFetcher mojang, FakeFetcher mineskin) {
        return new CompositeSkinService(
                new MojangSkins(new ImmediateScheduler(), new NoOpLogger(), mojang),
                new MineSkinService(
                        new ImmediateScheduler(), new NoOpLogger(), mineskin, null, java.time.Duration.ZERO));
    }

    private static Optional<NpcSkin> await(CompletableFuture<Optional<NpcSkin>> future) {
        assertThat(future).isCompleted();
        return future.join();
    }

    private static final class FakeFetcher implements HttpFetcher {
        private final java.util.Map<String, java.util.function.Function<URI, String>> bodies =
                new java.util.HashMap<>();
        private Optional<String> postResponse = Optional.empty();
        private int posts;

        void getResponse(String uri, String body) {
            bodies.put(uri, ignored -> body);
        }

        @Override
        public Optional<String> get(URI uri) {
            var supplier = bodies.get(uri.toString());
            return supplier == null ? Optional.empty() : Optional.of(supplier.apply(uri));
        }

        @Override
        public Optional<String> post(URI uri, String body) {
            posts++;
            return postResponse;
        }
    }

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
