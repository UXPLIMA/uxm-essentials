package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The production {@link HttpFetcher}: a JDK {@link HttpClient} GET/POST with bounded connect and request
 * timeouts so a remote outage can never hang the caller's thread. The body-or-empty methods map a {@code 200} to
 * its body and anything else to empty (the service's miss); the {@code exchange}/{@code exchangeGet} variants
 * return the full {@link HttpResponseView} (status + body + any {@code Retry-After}) the MineSkin v2 queue flow
 * branches on. A transport error or timeout yields {@link HttpResponseView#transportError()}. The blocking
 * {@code send} is fine here because the service only ever calls this off the tick thread through the scheduler.
 *
 * <p>The request timeout is per-instance: the Mojang lookup wires a short one (its endpoints answer instantly),
 * the MineSkin generate POST a longer one (generating a fresh texture from an image takes a few seconds). The
 * POST sends its body as {@code application/json}; a non-blank API key is sent as an {@code Authorization: Bearer}
 * header.
 *
 * <p>An {@link InterruptedException} restores the thread's interrupt flag before returning empty, so a shutdown
 * that interrupts the async pool is observed rather than swallowed.
 */
@NullMarked
public final class HttpClientFetcher implements HttpFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5L);
    /** The default request timeout, suitable for the instant Mojang endpoints. */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5L);

    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RETRY_AFTER = "Retry-After";
    private static final int HTTP_OK = 200;

    private final HttpClient client;
    private final Logger log;
    private final Duration requestTimeout;

    /** A fetcher with the {@link #DEFAULT_REQUEST_TIMEOUT}, for the instant Mojang endpoints. */
    public HttpClientFetcher(Logger log) {
        this(log, DEFAULT_REQUEST_TIMEOUT);
    }

    /** A fetcher with an explicit per-request timeout, for slower endpoints like MineSkin generation. */
    public HttpClientFetcher(Logger log, Duration requestTimeout) {
        this.log = Objects.requireNonNull(log, "log");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Optional<String> get(URI uri) {
        return get(uri, null);
    }

    @Override
    public Optional<String> get(URI uri, @Nullable String authToken) {
        Objects.requireNonNull(uri, "uri");
        HttpResponseView view = exchangeGet(uri, authToken);
        return view.status() == HTTP_OK ? view.body() : Optional.empty();
    }

    @Override
    public Optional<String> post(URI uri, String body) {
        HttpResponseView view = exchange(uri, body, null);
        return view.status() == HTTP_OK ? view.body() : Optional.empty();
    }

    @Override
    public HttpResponseView exchange(URI uri, String body, @Nullable String authToken) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(body, "body");
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("Accept", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        return send(withAuth(builder, authToken).build(), "POST");
    }

    @Override
    public HttpResponseView exchangeGet(URI uri, @Nullable String authToken) {
        Objects.requireNonNull(uri, "uri");
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", JSON_CONTENT_TYPE)
                .GET();
        return send(withAuth(builder, authToken).build(), "GET");
    }

    /** Attach the {@code Authorization: Bearer} header when a non-blank token is supplied, else leave it off. */
    private static HttpRequest.Builder withAuth(HttpRequest.Builder builder, @Nullable String authToken) {
        if (authToken != null && !authToken.isBlank()) {
            builder.header(AUTHORIZATION, BEARER_PREFIX + authToken.strip());
        }
        return builder;
    }

    private HttpResponseView send(HttpRequest request, String verb) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResponseView(response.statusCode(), Optional.of(response.body()), retryAfter(response));
        } catch (IOException network) {
            log.debug("{} to {} failed: {}", verb, request.uri(), String.valueOf(network));
            return HttpResponseView.transportError();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return HttpResponseView.transportError();
        }
    }

    /** The {@code Retry-After} delay in seconds when the server sent a numeric one (a {@code 429} backoff). */
    private static OptionalLong retryAfter(HttpResponse<String> response) {
        return response.headers()
                .firstValue(RETRY_AFTER)
                .map(HttpClientFetcher::parseSeconds)
                .orElse(OptionalLong.empty());
    }

    private static OptionalLong parseSeconds(String raw) {
        try {
            return OptionalLong.of(Long.parseLong(raw.strip()));
        } catch (NumberFormatException notSeconds) {
            // Retry-After can also be an HTTP date; we only honour the simpler delta-seconds form and fall back
            // to the service's own bounded backoff otherwise.
            return OptionalLong.empty();
        }
    }
}
