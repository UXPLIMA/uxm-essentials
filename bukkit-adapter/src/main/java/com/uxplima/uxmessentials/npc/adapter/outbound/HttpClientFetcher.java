package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The production {@link HttpFetcher}: a JDK {@link HttpClient} GET/POST with bounded connect and request
 * timeouts so a remote outage can never hang the caller's thread. A {@code 200} returns its body; any other
 * status, a timeout, or a transport error returns empty so the service treats it as a miss. The blocking
 * {@code send} is fine here because the service only ever calls this off the tick thread through the scheduler.
 *
 * <p>The request timeout is per-instance: the Mojang lookup wires a short one (its endpoints answer instantly),
 * the MineSkin generate POST a longer one (generating a fresh texture from an image takes a few seconds). The
 * POST sends its body as {@code application/json}.
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
        Objects.requireNonNull(uri, "uri");
        HttpRequest request =
                HttpRequest.newBuilder(uri).timeout(requestTimeout).GET().build();
        return send(request, "GET");
    }

    @Override
    public Optional<String> post(URI uri, String body) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(body, "body");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("Accept", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request, "POST");
    }

    private Optional<String> send(HttpRequest request, String verb) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == HTTP_OK ? Optional.of(response.body()) : Optional.empty();
        } catch (IOException network) {
            log.debug("{} to {} failed: {}", verb, request.uri(), String.valueOf(network));
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
