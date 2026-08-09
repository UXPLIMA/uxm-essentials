package com.uxplima.uxmessentials.rest.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/**
 * The listener over a real socket, because everything below it can be right while the thing as a whole never
 * answers: a bind that did not happen, a response that was not flushed, a connection that was not closed.
 */
class RestServerTest {

    private static final Logger QUIET = quietLogger();

    @Test
    void aRoutedRequestIsAnsweredAndAttributedToWhoeverAuthenticated() throws IOException {
        Router router = new Router().add(Route.of("GET", "/api/v1/whoami", "read", request -> {
            com.google.gson.JsonObject payload = Json.object();
            payload.addProperty("caller", request.caller());
            return Json.ok(payload);
        }));

        try (RestServer server = start(router, (request, route) -> RestServer.RequestFilter.Decision.accept("panel"))) {
            String answer = call(server.port(), "GET /api/v1/whoami HTTP/1.1\r\nHost: localhost\r\n\r\n");

            assertThat(answer).startsWith("HTTP/1.1 200 OK");
            assertThat(answer).contains("\"caller\":\"panel\"");
        }
    }

    @Test
    void anUnknownPathIsAnEnvelopeRatherThanAnEmptyBody() throws IOException {
        try (RestServer server = start(new Router(), RestServer.RequestFilter.allowing())) {
            String answer = call(server.port(), "GET /nowhere HTTP/1.1\r\n\r\n");

            assertThat(answer).startsWith("HTTP/1.1 404 Not Found");
            assertThat(answer).contains("\"code\":\"no-route\"");
        }
    }

    @Test
    void aFilterThatRefusesIsWhatTheClientGets() throws IOException {
        Router router = new Router().add(Route.of("GET", "/api/v1/whoami", "read", request -> Json.done()));
        RestServer.RequestFilter refusing = (request, route) ->
                RestServer.RequestFilter.Decision.refuse(Json.error(HttpStatus.UNAUTHORIZED, "unauthorized", "no"));

        try (RestServer server = start(router, refusing)) {
            assertThat(call(server.port(), "GET /api/v1/whoami HTTP/1.1\r\n\r\n"))
                    .startsWith("HTTP/1.1 401 Unauthorized");
        }
    }

    @Test
    void theWrongVerbOnAKnownPathSaysSo() throws IOException {
        Router router = new Router().add(Route.of("POST", "/api/v1/whoami", "write", request -> Json.done()));

        try (RestServer server = start(router, RestServer.RequestFilter.allowing())) {
            assertThat(call(server.port(), "GET /api/v1/whoami HTTP/1.1\r\n\r\n"))
                    .startsWith("HTTP/1.1 405 Method Not Allowed")
                    .contains("\"code\":\"wrong-method\"");
        }
    }

    @Test
    void aHandlerThatThrowsBecomesAFiveHundredWithNothingLeakedIntoTheBody() throws IOException {
        Router router = new Router().add(Route.of("GET", "/api/v1/boom", "read", request -> {
            throw new IllegalStateException("the connection string is postgres://user:hunter2@db");
        }));

        try (RestServer server = start(router, RestServer.RequestFilter.allowing())) {
            String answer = call(server.port(), "GET /api/v1/boom HTTP/1.1\r\n\r\n");

            assertThat(answer).startsWith("HTTP/1.1 500 Internal Server Error");
            assertThat(answer).doesNotContain("hunter2");
        }
    }

    @Test
    void aMalformedRequestIsAnsweredRatherThanDroppedSilently() throws IOException {
        try (RestServer server = start(new Router(), RestServer.RequestFilter.allowing())) {
            assertThat(call(server.port(), "NONSENSE\r\n\r\n")).startsWith("HTTP/1.1 400 Bad Request");
        }
    }

    @Test
    void closingReleasesThePort() throws IOException {
        RestServer server = start(new Router(), RestServer.RequestFilter.allowing());
        int port = server.port();
        server.close();

        try (RestServer again =
                RestServer.start("127.0.0.1", port, new Router(), RestServer.RequestFilter.allowing(), QUIET)) {
            assertThat(again.port()).isEqualTo(port);
        }
    }

    /** The server logs a thrown handler at SEVERE, and one test throws on purpose; keep it out of the output. */
    private static Logger quietLogger() {
        Logger logger = Logger.getLogger(RestServerTest.class.getName());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static RestServer start(Router router, RestServer.RequestFilter filter) throws IOException {
        return RestServer.start("127.0.0.1", 0, router, filter, QUIET);
    }

    /** Send one request and read everything the server sends back before it closes. */
    private static String call(int port, String request) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
