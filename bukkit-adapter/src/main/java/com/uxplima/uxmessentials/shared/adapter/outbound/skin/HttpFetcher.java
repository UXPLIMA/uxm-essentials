package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import java.net.URI;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The HTTP seam {@link MojangSkinService} (a two-step GET dance) and {@link MineSkinService} (the MineSkin
 * generate/queue calls) fetch over, so each service's orchestration, parsing and caching are unit-testable
 * against a fake without a live network. The production realisation wraps a JDK {@code HttpClient}; a test
 * supplies a map of canned bodies.
 *
 * <p>The body-or-empty contract is fail-soft on both verbs: a {@code 200} returns its response body, and any
 * non-{@code 200} status, timeout, or transport error returns an empty {@link Optional} (so a {@code 404} for an
 * unknown username, or a {@code 429} rate limit from MineSkin, is just an empty result the service treats as a
 * miss). The MineSkin v2 flow needs the finer {@link #exchange exchange} variant instead: it must see the status
 * code (to tell a {@code 200} inline skin from a {@code 202} queued job from a {@code 429} rate limit) and the
 * body even on a non-{@code 200} (a queued response carries the job id). {@code exchange} returns an
 * {@link HttpResponseView}; a transport error/timeout yields {@link HttpResponseView#transportError()}. No method
 * throws.
 *
 * <p>An optional {@code authToken} is sent as an {@code Authorization: Bearer <token>} header when non-blank
 * (MineSkin's API key raises the rate limit); a {@code null} token sends no header (the unauthenticated public
 * tier, fine for occasional skinning).
 *
 * <p>The {@code post}/{@code exchange} overloads default to empty so a GET-only fake (the Mojang service's test
 * seam) need not implement them; the production {@link HttpClientFetcher} overrides them.
 */
@NullMarked
public interface HttpFetcher {

    /** The body of a {@code 200} GET to {@code uri}, or empty for a non-{@code 200}, timeout, or transport error. */
    Optional<String> get(URI uri);

    /**
     * The body of a {@code 200} GET to {@code uri} with an optional {@code Authorization: Bearer} token, or empty
     * for a non-{@code 200}, timeout, or transport error. Defaults to the unauthenticated {@link #get(URI)} so a
     * fetcher that never authenticates need not override it.
     */
    default Optional<String> get(URI uri, @Nullable String authToken) {
        return get(uri);
    }

    /**
     * The body of a {@code 200} POST of the JSON {@code body} to {@code uri}, or empty for a non-{@code 200}
     * status (including a {@code 429} rate limit), timeout, or transport error. Defaults to empty so a GET-only
     * fake need not override it.
     */
    default Optional<String> post(URI uri, String body) {
        return Optional.empty();
    }

    /**
     * POST the JSON {@code body} to {@code uri} with an optional {@code Authorization: Bearer} token, returning
     * the full {@link HttpResponseView} (status + body + any {@code Retry-After}) so the caller can branch on the
     * status rather than collapse everything to a body-or-empty. Defaults to a {@code 200}/empty view wrapping
     * {@link #post(URI, String)} so an existing fake keeps working.
     */
    default HttpResponseView exchange(URI uri, String body, @Nullable String authToken) {
        return post(uri, body)
                .map(received -> HttpResponseView.of(200, received))
                .orElseGet(HttpResponseView::transportError);
    }

    /**
     * GET {@code uri} with an optional token, returning the full {@link HttpResponseView} so the queue-poll can
     * tell a ready job ({@code 200} with the skin) from a still-pending one. Defaults to wrapping
     * {@link #get(URI, String)}.
     */
    default HttpResponseView exchangeGet(URI uri, @Nullable String authToken) {
        return get(uri, authToken)
                .map(received -> HttpResponseView.of(200, received))
                .orElseGet(HttpResponseView::transportError);
    }
}
