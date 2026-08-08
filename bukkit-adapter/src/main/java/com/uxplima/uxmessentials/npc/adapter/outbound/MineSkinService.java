package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HttpFetcher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Generates a signed Minecraft skin from a custom image URL through the MineSkin v2 service, the source for
 * {@code /npc skin <name> url:<image-url>}. The v2 API is queue-based and auth-aware: a {@code POST} to
 * {@link #GENERATE_V2_ENDPOINT} with {@code {"url":...,"visibility":"unlisted"}} returns the signed texture
 * inline when generation is immediate, or a job id to poll {@link #QUEUE_V2_ENDPOINT}{@code <jobId>} until it is
 * ready. An optional API key (config {@code skin.mineskin-api-key}) is sent as an {@code Authorization: Bearer}
 * header to raise the rate limit; the unauthenticated tier still works for light operator use. The v2 wire flow,
 * defensive parsing ({@link MineSkinJson}, across the v2 {@code skin.texture.data.*} and the legacy v1
 * {@code data.texture.*} shapes) and bounded queue-poll / rate-limit backoff live in {@link MineSkinV2}; this
 * class owns input validation, the async hop, the fail-soft contract, and the URL-keyed cache.
 *
 * <p>The blocking HTTP and the bounded inter-poll/backoff sleep run on the {@link Scheduler}'s async pool, never a
 * tick thread; the returned future is completed from there. Every miss — a malformed image URL, a generation
 * error, a rate limit ({@code 429}) that outlasts the bounded backoff, a queued job not ready within the bounded
 * polls, a timeout, a response carrying no texture, malformed JSON, or any unexpected throw from the fetcher or
 * parser — completes the future with {@link Optional#empty()} after logging the cause with the URL; the future
 * never completes exceptionally and never stays uncompleted, so the command never hangs on the "generating" line.
 * Two bounded Caffeine caches keyed by the image URL hold the result: a generated texture is held for several
 * hours (a stable URL's texture does not change, so a re-skin of the same image is served from cache rather than
 * re-generated against the rate-limited endpoint), while a miss is held only briefly so a transient outage does
 * not block that URL for the full positive window once MineSkin recovers.
 *
 * <p>Maintainer-verify: the exact v2 endpoints, the {@code visibility} enum, whether a queue submit answers
 * {@code 200}-with-skin or {@code 202}-with-job, and the JSON paths are read from the documented v2 API (no live
 * MineSkin in tests). They are kept as the constants below plus one {@link MineSkinJson} parser, so a maintainer
 * can confirm and adjust them in one place; an API key raises the rate limit.
 */
@NullMarked
public final class MineSkinService {

    /** The documented v2 generate endpoint: queue generation and return the result (inline or a job to poll). */
    public static final String GENERATE_V2_ENDPOINT = "https://api.mineskin.org/v2/generate";

    /** The v2 queue-poll base; the job id is appended ({@code .../v2/queue/<jobId>}). */
    public static final String QUEUE_V2_ENDPOINT = "https://api.mineskin.org/v2/queue/";

    /** v2 visibility is a string (not the v1 int); {@code unlisted} keeps the skin out of the public gallery. */
    public static final String VISIBILITY = "unlisted";

    /** Bounded queue polls before a queued job is given up as a miss. */
    public static final int MAX_POLL_ATTEMPTS = 6;

    /** Bounded retries of a {@code 429}-rate-limited generate before failing soft. */
    public static final int MAX_RATE_LIMIT_RETRIES = 2;

    /** Longer than the Mojang lookup: generating a fresh texture from an image takes a few seconds. */
    public static final Duration GENERATE_TIMEOUT = Duration.ofSeconds(12L);

    /** The default bounded delay between queue polls (and the rate-limit backoff when no {@code Retry-After}). */
    public static final Duration DEFAULT_POLL_DELAY = Duration.ofMillis(800L);

    private static final long MAX_CACHED_URLS = 256L;
    /** A generated texture for a stable image URL does not change, so a hit is held for the rest of the day. */
    private static final Duration POSITIVE_TTL = Duration.ofHours(12L);
    /** A miss is a transient failure as often as not (a 429/5xx/timeout), so it is held only briefly. */
    private static final Duration NEGATIVE_TTL = Duration.ofMinutes(5L);

    private final Scheduler scheduler;
    private final Logger log;
    private final MineSkinV2 v2;
    private final Cache<String, Optional<NpcSkin>> positive;
    private final Cache<String, Optional<NpcSkin>> negative;

    /** Wires the v2 flow with the default {@link #DEFAULT_POLL_DELAY}; {@code apiKey} may be blank/null. */
    public MineSkinService(Scheduler scheduler, Logger log, HttpFetcher fetcher, @Nullable String apiKey) {
        this(scheduler, log, fetcher, apiKey, DEFAULT_POLL_DELAY);
    }

    /** Wires the v2 flow with an explicit bounded poll/backoff delay (tests pass {@link Duration#ZERO}). */
    public MineSkinService(
            Scheduler scheduler, Logger log, HttpFetcher fetcher, @Nullable String apiKey, Duration pollDelay) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        Objects.requireNonNull(fetcher, "fetcher");
        Objects.requireNonNull(pollDelay, "pollDelay");
        this.v2 = new MineSkinV2(
                fetcher,
                log,
                URI.create(GENERATE_V2_ENDPOINT),
                QUEUE_V2_ENDPOINT,
                (apiKey == null || apiKey.isBlank()) ? null : apiKey,
                VISIBILITY,
                MAX_POLL_ATTEMPTS,
                MAX_RATE_LIMIT_RETRIES,
                pollDelay,
                MineSkinV2.Sleeper::blocking);
        this.positive = Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_URLS)
                .expireAfterWrite(POSITIVE_TTL)
                .build();
        this.negative = Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_URLS)
                .expireAfterWrite(NEGATIVE_TTL)
                .build();
    }

    /**
     * Generate {@code imageUrl}'s signed skin off-thread, completing with the present skin or with
     * {@link Optional#empty()} for any miss. Never completes exceptionally; never blocks the calling thread.
     */
    public CompletableFuture<Optional<NpcSkin>> fetchFromUrl(String imageUrl) {
        Objects.requireNonNull(imageUrl, "imageUrl");
        String key = imageUrl.strip();
        if (key.isEmpty() || !isUsableImageUrl(key)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<NpcSkin> hit = positive.getIfPresent(key);
        if (hit != null) {
            return CompletableFuture.completedFuture(hit);
        }
        Optional<NpcSkin> miss = negative.getIfPresent(key);
        if (miss != null) {
            return CompletableFuture.completedFuture(miss);
        }
        CompletableFuture<Optional<NpcSkin>> result = new CompletableFuture<>();
        // Guarantee the future always completes: a throw from the generate seam (the injected fetcher, gson, the
        // cache) must not escape the async stage and orphan the future, or the operator hangs on the "generating"
        // line forever. Any unexpected throw completes the future empty after logging.
        scheduler.async(() -> {
            try {
                result.complete(load(key));
            } catch (RuntimeException unexpected) {
                log.error("Unexpected failure generating MineSkin skin for image URL " + key, unexpected);
                result.complete(Optional.empty());
            }
        });
        return result;
    }

    /**
     * The blocking v2 generate for an already-validated {@code imageUrl}, caching and returning the outcome. A
     * generated texture is cached long (a stable URL's texture does not change); a miss — an error, a {@code 429}
     * rate limit, a timeout, a textureless response, an exhausted poll — is cached only briefly so a transient
     * MineSkin outage does not block that URL for the full positive TTL, while still absorbing a burst of retries.
     */
    private Optional<NpcSkin> load(String imageUrl) {
        Optional<NpcSkin> skin = v2.generate(imageUrl);
        if (skin.isPresent()) {
            positive.put(imageUrl, skin);
        } else {
            negative.put(imageUrl, skin);
        }
        return skin;
    }

    /**
     * Whether {@code spec} looks like a fetchable image URL: a syntactically valid http/https URI with a host.
     * A garbage or relative string is rejected as a miss up front, so the rate-limited endpoint is never POSTed a
     * URL it can only reject anyway.
     */
    private static boolean isUsableImageUrl(String spec) {
        URI uri;
        try {
            uri = URI.create(spec);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        String scheme = uri.getScheme();
        return uri.getHost() != null
                && scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
    }
}
