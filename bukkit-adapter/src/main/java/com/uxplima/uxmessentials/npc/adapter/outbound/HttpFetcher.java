package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.net.URI;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * The HTTP seam {@link MojangSkinService} (a two-step GET dance) and {@link MineSkinService} (a single JSON
 * POST) fetch over, so each service's orchestration, parsing and caching are unit-testable against a fake
 * without a live network. The production realisation wraps a JDK {@code HttpClient}; a test supplies a map of
 * canned bodies.
 *
 * <p>The contract is fail-soft on both verbs: a {@code 200} returns its response body, and any non-{@code 200}
 * status, timeout, or transport error returns an empty {@link Optional} (so a {@code 404} for an unknown
 * username, or a {@code 429} rate limit from MineSkin, is just an empty result the service treats as a miss).
 * Neither method throws.
 *
 * <p>{@link #post(URI, String)} defaults to empty so a GET-only fake (the Mojang service's test seam) need not
 * implement it; the production {@link HttpClientFetcher} overrides both.
 */
@NullMarked
public interface HttpFetcher {

    /** The body of a {@code 200} GET to {@code uri}, or empty for a non-{@code 200}, timeout, or transport error. */
    Optional<String> get(URI uri);

    /**
     * The body of a {@code 200} POST of the JSON {@code body} to {@code uri}, or empty for a non-{@code 200}
     * status (including a {@code 429} rate limit), timeout, or transport error. Defaults to empty so a GET-only
     * fake need not override it.
     */
    default Optional<String> post(URI uri, String body) {
        return Optional.empty();
    }
}
