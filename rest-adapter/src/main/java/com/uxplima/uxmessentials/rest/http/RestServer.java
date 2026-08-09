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
 * <p>One path is not a request at all. A connection that asks to become a WebSocket is routed and authenticated
 * exactly like any other, and then, instead of an answer, the socket is handed to whatever {@link Upgrade} was
 * installed and stays open for as long as that keeps it.
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
    private final Upgrade upgrade;
    private final Logger log;

    private RestServer(ServerSocket socket, Router router, RequestFilter filter, Upgrade upgrade, Logger log) {
        this.socket = socket;
        this.router = router;
        this.filter = filter;
        this.upgrade = upgrade;
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
        return start(bind, port, router, filter, Upgrade.none(), log);
    }

    /**
     * Bind {@code bind:port} and start answering, with a path that becomes a WebSocket.
     *
     * @param upgrade what takes over a connection asking to be upgraded
     */
    public static RestServer start(
            String bind, int port, Router router, RequestFilter filter, Upgrade upgrade, Logger log)
            throws IOException {
        Objects.requireNonNull(bind, "bind");
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(upgrade, "upgrade");
        Objects.requireNonNull(log, "log");
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getByName(bind), port));
        RestServer server = new RestServer(socket, router, filter, upgrade, log);
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

            HttpRequest request;
            try {
                request = RequestReader.read(in);
            } catch (HttpException refused) {
                answer(out, Json.error(refused.status(), "bad-request", String.valueOf(refused.getMessage())));
                return;
            }
            if (upgrade.handles(request)) {
                upgrade(request, open, out);
                return;
            }
            answer(out, dispatch(router, filter, request, log));
        } catch (IOException gone) {
            // The client hung up or timed out. There is nobody left to tell, and a stack trace per dropped
            // connection would be the loudest thing in the log.
            log.log(Level.FINE, "connection ended before it was answered", gone);
        }
    }

    /**
     * Hand a connection over, once it has been routed and authenticated like any other request.
     *
     * <p>The route lookup and the filter are not skipped for an upgrade. A stream is a way of reading the server,
     * so it needs a token with the scope for it, and putting the endpoint in the route table is what makes that
     * true rather than a promise in a comment.
     */
    private void upgrade(HttpRequest request, Socket open, OutputStream out) throws IOException {
        Optional<Router.Match> match = router.find(request);
        if (match.isEmpty()) {
            answer(out, Json.error(HttpStatus.NOT_FOUND, "no-route", "nothing answers " + request.path()));
            return;
        }
        RequestFilter.Decision decision = filter.before(request, match.get().route());
        if (decision.refusal().isPresent()) {
            answer(out, decision.refusal().get());
            return;
        }
        upgrade.take(request, decision.caller(), open).ifPresent(refusal -> {
            try {
                answer(out, refusal);
            } catch (IOException gone) {
                log.log(Level.FINE, "the connection ended before the upgrade could be refused", gone);
            }
        });
    }

    private static void answer(OutputStream out, HttpResponse response) throws IOException {
        out.write(response.toBytes());
        out.flush();
    }

    /**
     * Find the route for a request that has already been read, ask the filter, and run it.
     *
     * <p>Public because it is the whole of what happens to a request once it is off the wire, and a test that
     * exercises a route should go through the same code a socket does rather than a second copy of it.
     */
    public static HttpResponse dispatch(Router router, RequestFilter filter, HttpRequest request, Logger log) {
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
            case HttpStatus.GATEWAY_TIMEOUT -> "timed-out";
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

    /**
     * What takes a connection over instead of answering it.
     *
     * <p>One implementation, for the event stream, and the interface exists so the HTTP layer does not have to know
     * what a WebSocket is: this file's job ends at "routed, authenticated, and no longer mine".
     */
    public interface Upgrade {

        /** Whether this would take that request, asked before the route is looked up. */
        boolean handles(HttpRequest request);

        /**
         * Take the connection, and keep it for as long as it lives.
         *
         * @return a response to send instead, when the request could not be upgraded after all
         */
        Optional<HttpResponse> take(HttpRequest request, String caller, Socket socket) throws IOException;

        /** An upgrade nothing matches, for a listener serving requests and nothing else. */
        static Upgrade none() {
            return new Upgrade() {
                @Override
                public boolean handles(HttpRequest request) {
                    return false;
                }

                @Override
                public Optional<HttpResponse> take(HttpRequest request, String caller, Socket socket) {
                    throw new IllegalStateException("this listener upgrades nothing");
                }
            };
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
