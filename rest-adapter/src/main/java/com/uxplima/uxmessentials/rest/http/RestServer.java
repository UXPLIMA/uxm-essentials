package com.uxplima.uxmessentials.rest.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The listener: accept a connection, read one request, answer it, close.
 *
 * <p>A virtual thread per connection, so a slow client costs a stack rather than a platform thread and the accept
 * loop never waits on one. Nothing here runs on a server thread: the handler behind a route calls the published
 * API, which hops to whatever thread owns the work and hands back a future.
 *
 * <p>Closing is ordinary. The socket closes first, which wakes the accept loop out of {@code accept()} with the
 * exception it is expecting, and the executor is then given a moment to finish whatever it was already answering.
 */
public final class RestServer implements AutoCloseable {

    /** How long a connection may say nothing before it is dropped. */
    static final int READ_TIMEOUT_MILLIS = 10_000;

    /** How long a close waits for requests already in flight. */
    private static final long SHUTDOWN_SECONDS = 5;

    private final ServerSocket socket;
    private final ExecutorService connections;
    private final Router router;
    private final RequestFilter filter;
    private final Logger log;

    private RestServer(ServerSocket socket, Router router, RequestFilter filter, Logger log) {
        this.socket = socket;
        this.router = router;
        this.filter = filter;
        this.log = log;
        this.connections = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Bind {@code bind:port} and start answering.
     *
     * @param filter what runs before the route, for anything that can refuse a request outright
     */
    public static RestServer start(String bind, int port, Router router, RequestFilter filter, Logger log)
            throws IOException {
        Objects.requireNonNull(bind, "bind");
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(log, "log");
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getByName(bind), port));
        RestServer server = new RestServer(socket, router, filter, log);
        server.connections.execute(server::acceptLoop);
        return server;
    }

    /** The port actually bound, which a test asking for port zero needs to know. */
    public int port() {
        return socket.getLocalPort();
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try {
                Socket client = socket.accept();
                connections.execute(() -> serve(client));
            } catch (IOException failure) {
                if (!socket.isClosed()) {
                    log.log(Level.WARNING, "could not accept a connection", failure);
                }
            }
        }
    }

    private void serve(Socket client) {
        try (Socket open = client) {
            open.setSoTimeout(READ_TIMEOUT_MILLIS);
            InputStream in = open.getInputStream();
            OutputStream out = open.getOutputStream();
            HttpResponse response = answer(in);
            out.write(response.toBytes());
            out.flush();
        } catch (IOException gone) {
            // The client hung up or timed out. There is nobody left to tell, and a stack trace per dropped
            // connection would be the loudest thing in the log.
            log.log(Level.FINE, "connection ended before it was answered", gone);
        }
    }

    /** Read, filter, route, run: the four things that happen to a request, each able to end it. */
    private HttpResponse answer(InputStream in) throws IOException {
        HttpRequest request;
        try {
            request = RequestReader.read(in);
        } catch (HttpException refused) {
            return Json.error(refused.status(), "bad-request", String.valueOf(refused.getMessage()));
        }
        try {
            Optional<Router.Match> match = router.find(request);
            if (match.isEmpty()) {
                return Json.error(HttpStatus.NOT_FOUND, "no-route", "nothing answers " + request.path());
            }
            RequestFilter.Decision decision = filter.before(request, match.get().route());
            if (decision.refusal().isPresent()) {
                return decision.refusal().get();
            }
            RestRequest attributed = match.get().request().withCaller(decision.caller());
            return match.get().route().handler().handle(attributed);
        } catch (HttpException refused) {
            return Json.error(refused.status(), codeFor(refused.status()), String.valueOf(refused.getMessage()));
        } catch (RuntimeException failure) {
            // The detail belongs in the operator's log, not in the body: a consumer cannot act on a stack trace
            // and an attacker should not be handed one.
            log.log(Level.SEVERE, "a request handler threw", failure);
            return Json.error(HttpStatus.INTERNAL_ERROR, "internal-error", "the request could not be completed");
        }
    }

    private static String codeFor(int status) {
        return switch (status) {
            case HttpStatus.METHOD_NOT_ALLOWED -> "wrong-method";
            case HttpStatus.NOT_IMPLEMENTED -> "not-implemented";
            case HttpStatus.PAYLOAD_TOO_LARGE -> "too-large";
            case HttpStatus.SERVICE_UNAVAILABLE -> "module-off";
            default -> "bad-request";
        };
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException failure) {
            log.log(Level.FINE, "the listener socket was already closed", failure);
        }
        connections.shutdown();
        try {
            if (!connections.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                connections.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            connections.shutdownNow();
        }
    }

    /** What runs between finding the route and running it: authentication, scopes, rate limits. */
    @FunctionalInterface
    public interface RequestFilter {

        /** Either the response to send instead of running the route, or the name to attribute it to. */
        Decision before(HttpRequest request, Route route);

        /** A filter that refuses nothing, which is what the tests for the layers below it want. */
        static RequestFilter allowing() {
            return (request, route) -> Decision.accept("test");
        }

        /**
         * What the filter decided.
         *
         * @param refusal the response to send instead, or empty to let the route run
         * @param caller who to attribute the request to, meaningful only when it is being let through
         */
        record Decision(Optional<HttpResponse> refusal, String caller) {

            public Decision {
                Objects.requireNonNull(refusal, "refusal");
                Objects.requireNonNull(caller, "caller");
            }

            /** Let it through, attributed to {@code caller}. */
            public static Decision accept(String caller) {
                return new Decision(Optional.empty(), caller);
            }

            /** Send this instead. */
            public static Decision refuse(HttpResponse response) {
                return new Decision(Optional.of(response), "refused");
            }
        }
    }
}
