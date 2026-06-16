package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Unit-tests {@link MineSkinService}'s single-POST generate flow, defensive parsing, fail-soft empties and
 * URL-keyed caching against a fake HTTP seam — no live MineSkin call is ever made. The fake records each POST
 * (uri + body) and returns a canned response (or empty, modelling a 429/error/timeout); a deferred scheduler
 * runs the async work synchronously so the returned future is already complete when the test reads it.
 */
class MineSkinServiceTest {

    private static final String IMAGE_URL = "https://example.com/skin.png";
    private static final String GENERATED =
            "{\"data\":{\"texture\":{\"value\":\"Z2VuZXJhdGVk\",\"signature\":\"genSig=\"}}}";

    @Test
    void generatesASignedSkinFromAnImageUrl() {
        FakePostFetcher fetcher = new FakePostFetcher();
        fetcher.response = Optional.of(GENERATED);
        MineSkinService service = newService(fetcher);

        Optional<NpcSkin> skin = await(service.fetchFromUrl(IMAGE_URL));

        assertThat(skin).contains(new NpcSkin("Z2VuZXJhdGVk", "genSig="));
        // The image URL is carried in the JSON POST body, not the request line.
        assertThat(fetcher.lastBody).contains(IMAGE_URL);
        assertThat(fetcher.posts).isEqualTo(1);
    }

    @Test
    void aRateLimitOrErrorResponseYieldsEmpty() {
        FakePostFetcher fetcher = new FakePostFetcher();
        // An empty seam result models a 429/5xx/timeout — the production fetcher maps all of those to empty.
        fetcher.response = Optional.empty();
        MineSkinService service = newService(fetcher);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
        assertThat(fetcher.posts).isEqualTo(1);
    }

    @Test
    void aResponseWithNoTextureYieldsEmpty() {
        FakePostFetcher fetcher = new FakePostFetcher();
        fetcher.response = Optional.of("{\"data\":{\"id\":7}}");
        MineSkinService service = newService(fetcher);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
    }

    @Test
    void malformedJsonYieldsEmpty() {
        FakePostFetcher fetcher = new FakePostFetcher();
        fetcher.response = Optional.of("not json at all {");
        MineSkinService service = newService(fetcher);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
    }

    @Test
    void aBlankUrlYieldsEmptyWithoutAnyPost() {
        FakePostFetcher fetcher = new FakePostFetcher();
        MineSkinService service = newService(fetcher);

        assertThat(await(service.fetchFromUrl("   "))).isEmpty();
        assertThat(fetcher.posts).isZero();
    }

    @Test
    void anImageUrlThatIsNotAValidUrlYieldsEmptyWithoutAnyPost() {
        FakePostFetcher fetcher = new FakePostFetcher();
        MineSkinService service = newService(fetcher);

        // A spaced/garbage string is not a usable image URL — reject it as a miss rather than POST it on.
        assertThat(await(service.fetchFromUrl("not a url"))).isEmpty();
        assertThat(fetcher.posts).isZero();
    }

    @Test
    void aSecondGenerateForTheSameUrlIsServedFromCache() {
        FakePostFetcher fetcher = new FakePostFetcher();
        fetcher.response = Optional.of(GENERATED);
        MineSkinService service = newService(fetcher);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isPresent();
        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isPresent();

        // A generated texture for a stable URL is cached, so the second call never re-POSTs.
        assertThat(fetcher.posts).isEqualTo(1);
    }

    @Test
    void afailedGenerateIsCachedSoItIsNotReGenerated() {
        FakePostFetcher fetcher = new FakePostFetcher();
        fetcher.response = Optional.empty();
        MineSkinService service = newService(fetcher);

        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();
        assertThat(await(service.fetchFromUrl(IMAGE_URL))).isEmpty();

        assertThat(fetcher.posts).isEqualTo(1);
    }

    @Test
    void aThrowingFetcherCompletesTheFutureEmptyRatherThanOrphaningIt() {
        FakePostFetcher fetcher = new FakePostFetcher();
        // Model a fetcher that throws an unchecked exception inside the async stage (a faulty/strict seam): the
        // future must still complete empty, never hang the operator on the "generating" line.
        fetcher.thrower = new IllegalStateException("boom");
        MineSkinService service = newService(fetcher);

        CompletableFuture<Optional<NpcSkin>> future = service.fetchFromUrl(IMAGE_URL);

        assertThat(future).isCompleted();
        assertThat(future).isNotCompletedExceptionally();
        assertThat(future.join()).isEmpty();
        assertThat(fetcher.posts).isEqualTo(1);
    }

    @Test
    void anIllegalUrlCompletesEmptyWithoutEverPosting() {
        // A throwing fetcher proves the no-POST guarantee: the validation rejects the spec before the async stage,
        // so the seam (which would throw if reached) is never touched and the future still completes empty.
        FakePostFetcher fetcher = new FakePostFetcher();
        fetcher.thrower = new IllegalStateException("must never be reached");
        MineSkinService service = newService(fetcher);

        CompletableFuture<Optional<NpcSkin>> future = service.fetchFromUrl("not a url");

        assertThat(future).isCompleted();
        assertThat(future.join()).isEmpty();
        assertThat(fetcher.posts).isZero();
    }

    @Test
    void theRequestBodyIsValidJsonCarryingTheUrlVerbatim() {
        FakePostFetcher fetcher = new FakePostFetcher();
        fetcher.response = Optional.of(GENERATED);
        MineSkinService service = newService(fetcher);
        // A real URL with query params and percent-escapes: the body must be parseable JSON whose url field is
        // the input verbatim and whose visibility is 0 — proving it is built (and escaped) through gson rather
        // than hand-concatenated, so an awkward URL can never break the body or inject a field.
        String url = "https://example.com/path/skin%20file.png?a=1&b=two";

        await(service.fetchFromUrl(url));

        JsonObject body = JsonParser.parseString(fetcher.lastBody).getAsJsonObject();
        assertThat(body.get("url").getAsString()).isEqualTo(url);
        assertThat(body.get("visibility").getAsInt()).isZero();
        assertThat(body.size()).isEqualTo(2);
    }

    private static MineSkinService newService(FakePostFetcher fetcher) {
        return new MineSkinService(new ImmediateScheduler(), new NoOpLogger(), fetcher);
    }

    private static Optional<NpcSkin> await(CompletableFuture<Optional<NpcSkin>> future) {
        assertThat(future).isCompleted();
        return future.join();
    }

    /**
     * A stubbed POST seam: records each POST (count + last body) and returns the canned response, or — when
     * {@code thrower} is set — throws it, modelling a faulty seam that must not orphan the service's future.
     */
    private static final class FakePostFetcher implements HttpFetcher {
        private Optional<String> response = Optional.empty();
        private @Nullable RuntimeException thrower;
        private int posts;
        private String lastBody = "";

        @Override
        public Optional<String> get(URI uri) {
            return Optional.empty();
        }

        @Override
        public Optional<String> post(URI uri, String body) {
            posts++;
            lastBody = body;
            if (thrower != null) {
                throw thrower;
            }
            return response;
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
