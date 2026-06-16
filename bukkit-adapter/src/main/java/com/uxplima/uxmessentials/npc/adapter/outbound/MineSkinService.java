package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
 * timeout, a response carrying no texture, malformed JSON — completes the future with {@link Optional#empty()}
 * after logging the cause with the URL; the future never completes exceptionally. A bounded Caffeine cache keyed
 * by the image URL holds the result (present or absent) for several hours: a generated texture for a stable URL
 * does not change, so a re-skin of the same image is served from cache rather than re-generated against the
 * rate-limited endpoint.
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

    private static final long MAX_CACHED_URLS = 256L;
    private static final Duration CACHE_TTL = Duration.ofHours(12L);

    private final Scheduler scheduler;
    private final Logger log;
    private final HttpFetcher fetcher;
    private final Cache<String, Optional<NpcSkin>> cache;

    public MineSkinService(Scheduler scheduler, Logger log, HttpFetcher fetcher) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_URLS)
                .expireAfterWrite(CACHE_TTL)
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
        Optional<NpcSkin> cached = cache.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<Optional<NpcSkin>> result = new CompletableFuture<>();
        scheduler.async(() -> result.complete(load(key)));
        return result;
    }

    /** The blocking generate POST for an already-validated {@code imageUrl}, caching and returning the outcome. */
    private Optional<NpcSkin> load(String imageUrl) {
        Optional<NpcSkin> skin = generate(imageUrl);
        cache.put(imageUrl, skin);
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

    /** The generate-from-URL JSON body: the image URL and a private visibility so the skin is not listed. */
    private static String requestBody(String imageUrl) {
        return "{\"url\":\"" + jsonEscape(imageUrl) + "\",\"visibility\":0}";
    }

    /** Escape the characters that would break a JSON string literal, so an odd URL cannot malform the body. */
    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
