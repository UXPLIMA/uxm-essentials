package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Generates a signed Minecraft skin from a custom image URL through the public MineSkin service, the source for
 * {@code /npc skin <name> url:<image-url>}. It is a single JSON POST: {@code {"url":"<imageUrl>","visibility":0}}
 * to {@link #GENERATE_URL_ENDPOINT}, whose response carries the base64 texture {@code value} and its
 * {@code signature} ({@link MineSkinJson} reads them across the shapes the API has used).
 *
 * <p>The blocking HTTP runs on the {@link Scheduler}'s async pool, never a tick thread; the returned future is
 * completed from there. Every miss — a malformed image URL, a generation error, a rate limit ({@code 429}), a
 * timeout, a response carrying no texture, malformed JSON, or any unexpected throw from the fetcher or parser —
 * completes the future with {@link Optional#empty()} after logging the cause with the URL; the future never
 * completes exceptionally and never stays uncompleted, so the command never hangs on the "generating" line. Two
 * bounded Caffeine caches keyed by the image URL hold the result: a generated texture is held for several hours
 * (a stable URL's texture does not change, so a re-skin of the same image is served from cache rather than
 * re-generated against the rate-limited endpoint), while a miss is held only briefly so a transient outage does
 * not block that URL for the full positive window once MineSkin recovers.
 *
 * <p>Heavy use of MineSkin wants an API key (a higher rate limit). This wires the unauthenticated public
 * endpoint, which suffices for occasional operator skinning; an operator hitting the rate limit needs an
 * authenticated key wired in — at that point {@link #GENERATE_URL_ENDPOINT} (and an {@code Authorization} header)
 * is the single place to adjust.
 */
@NullMarked
public final class MineSkinService {

    /**
     * The documented generate-from-URL endpoint. The classic public endpoint returns the signed texture under
     * {@code data.texture.{value,signature}} without an API key for light use; the v2 {@code /v2/generate}
     * endpoint is queue-based. Kept a single constant so a maintainer can repoint it (or move to v2) in one edit.
     */
    public static final String GENERATE_URL_ENDPOINT = "https://api.mineskin.org/generate/url";

    /** Longer than the Mojang lookup: generating a fresh texture from an image takes a few seconds. */
    public static final Duration GENERATE_TIMEOUT = Duration.ofSeconds(12L);

    /** Stateless and thread-safe; reused to build the request body without re-instantiating per call. */
    private static final Gson GSON = new Gson();

    private static final long MAX_CACHED_URLS = 256L;
    /** A generated texture for a stable image URL does not change, so a hit is held for the rest of the day. */
    private static final Duration POSITIVE_TTL = Duration.ofHours(12L);
    /** A miss is a transient failure as often as not (a 429/5xx/timeout), so it is held only briefly. */
    private static final Duration NEGATIVE_TTL = Duration.ofMinutes(5L);

    private final Scheduler scheduler;
    private final Logger log;
    private final HttpFetcher fetcher;
    private final Cache<String, Optional<NpcSkin>> positive;
    private final Cache<String, Optional<NpcSkin>> negative;

    public MineSkinService(Scheduler scheduler, Logger log, HttpFetcher fetcher) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
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
        if (key.isEmpty() || uri(GENERATE_URL_ENDPOINT).isEmpty() || !isUsableImageUrl(key)) {
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
     * The blocking generate POST for an already-validated {@code imageUrl}, caching and returning the outcome. A
     * generated texture is cached long (a stable URL's texture does not change); a miss — an error, a {@code 429}
     * rate limit, a timeout, a textureless response — is cached only briefly so a transient MineSkin outage does
     * not block that URL for the full positive TTL, while still absorbing a burst of command retries.
     */
    private Optional<NpcSkin> load(String imageUrl) {
        Optional<NpcSkin> skin = generate(imageUrl);
        if (skin.isPresent()) {
            positive.put(imageUrl, skin);
        } else {
            negative.put(imageUrl, skin);
        }
        return skin;
    }

    private Optional<NpcSkin> generate(String imageUrl) {
        Optional<URI> endpoint = uri(GENERATE_URL_ENDPOINT);
        if (endpoint.isEmpty()) {
            log.warn("MineSkin endpoint {} is not a valid URI", GENERATE_URL_ENDPOINT);
            return Optional.empty();
        }
        Optional<String> body = fetcher.post(endpoint.get(), requestBody(imageUrl));
        if (body.isEmpty()) {
            log.debug("MineSkin generate for image URL {} returned no body (error, rate limit, or timeout)", imageUrl);
            return Optional.empty();
        }
        Optional<NpcSkin> skin = MineSkinJson.skin(body.get());
        if (skin.isEmpty()) {
            log.warn("MineSkin generate response for image URL {} carried no texture value", imageUrl);
        }
        return skin;
    }

    /**
     * The generate-from-URL JSON body: the image URL and a private visibility so the skin is not listed. Built
     * through gson so the URL — which may carry a quote, a backslash, or a control character — is escaped exactly
     * as JSON requires and can never break the body or inject a field, rather than relying on hand-rolled escaping.
     */
    private static String requestBody(String imageUrl) {
        JsonObject body = new JsonObject();
        body.addProperty("url", imageUrl);
        body.addProperty("visibility", 0);
        return GSON.toJson(body);
    }

    /**
     * Whether {@code spec} looks like a fetchable image URL: a syntactically valid http/https URI with a host.
     * A garbage or relative string is rejected as a miss up front, so the rate-limited endpoint is never POSTed a
     * URL it can only reject anyway.
     */
    private static boolean isUsableImageUrl(String spec) {
        Optional<URI> parsed = uri(spec);
        if (parsed.isEmpty()) {
            return false;
        }
        URI uri = parsed.get();
        String scheme = uri.getScheme();
        return uri.getHost() != null
                && scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
    }

    private static Optional<URI> uri(String spec) {
        try {
            return Optional.of(URI.create(spec));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
