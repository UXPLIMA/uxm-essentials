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
 * The production {@link HttpFetcher}: a JDK {@link HttpClient} GET with bounded connect and request timeouts so
 * a Mojang outage can never hang the caller's thread. A {@code 200} returns its body; any other status, a
 * timeout, or a transport error returns empty so the {@link MojangSkinService} treats it as a miss. The blocking
 * {@code send} is fine here because the service only ever calls this off the tick thread through the scheduler.
 *
 * <p>An {@link InterruptedException} restores the thread's interrupt flag before returning empty, so a shutdown
 * that interrupts the async pool is observed rather than swallowed.
 */
@NullMarked
public final class HttpClientFetcher implements HttpFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5L);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5L);
    private static final int HTTP_OK = 200;

    private final HttpClient client;
    private final Logger log;

    public HttpClientFetcher(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Optional<String> get(URI uri) {
        Objects.requireNonNull(uri, "uri");
        HttpRequest request =
                HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == HTTP_OK ? Optional.of(response.body()) : Optional.empty();
        } catch (IOException network) {
            log.debug("Mojang GET to {} failed: {}", uri, String.valueOf(network));
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
