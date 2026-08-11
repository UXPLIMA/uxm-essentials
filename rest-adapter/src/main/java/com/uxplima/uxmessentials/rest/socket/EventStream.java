package com.uxplima.uxmessentials.rest.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.rest.http.HttpRequest;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestServer;

/**
 * Everybody currently listening, and the one method that sends something to all of them.
 *
 * <p>Polling an HTTP endpoint to find out whether anything happened is the thing this exists to replace. A panel
 * that wants to know when somebody is banned should be told when somebody is banned, and asking every two seconds
 * whether the answer has changed is how a small integration becomes a load problem.
 *
 * <p>The fan-out renders once and writes the same text to every subscriber that asked for it. Nothing waits on
 * anything: a socket that has gone is dropped by the writing thread, and its own thread finds out when its next
 * read fails.
 *
 * <p>How many may be open at once is capped. Every open stream is a socket and a thread held for as long as the
 * client wants them, so a client reconnecting in a loop is the one shape of traffic this listener cannot shrug
 * off the way it shrugs off a request.
 */
public final class EventStream implements RestServer.Upgrade, AutoCloseable {

    private final String path;
    private final int maxSubscribers;
    private final Set<EventSocket> live = ConcurrentHashMap.newKeySet();
    private final Logger log;

    /**
     * @param path the one path this upgrades, which is a route in the table so it is authenticated like any other
     * @param maxSubscribers how many connections may be open at once, across every token
     */
    public EventStream(String path, int maxSubscribers, Logger log) {
        this.path = Objects.requireNonNull(path, "path");
        if (maxSubscribers < 1) {
            throw new IllegalArgumentException("maxSubscribers must be at least one: " + maxSubscribers);
        }
        this.maxSubscribers = maxSubscribers;
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean handles(HttpRequest request) {
        return path.equals(request.path());
    }

    @Override
    public Optional<HttpResponse> take(HttpRequest request, String caller, Socket socket) throws IOException {
        Optional<HttpResponse> refusal = Handshake.refusalFor(request);
        if (refusal.isPresent()) {
            return refusal;
        }
        if (live.size() >= maxSubscribers) {
            log.warning("Refused an event stream for " + caller + ": already " + maxSubscribers
                    + " connections, which is max-subscribers in rest.conf.");
            return Optional.of(Json.error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "too-many-subscribers",
                    "this listener already has " + maxSubscribers + " event streams open"));
        }
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        out.write(Handshake.acceptFor(request).orElseThrow());
        out.flush();

        EventSocket subscriber = new EventSocket(socket, in, out, caller);
        live.add(subscriber);
        log.log(Level.FINE, "event stream opened for {0}", caller);
        try {
            subscriber.listen();
        } finally {
            live.remove(subscriber);
            log.log(Level.FINE, "event stream closed for {0}", caller);
        }
        return Optional.empty();
    }

    /**
     * Send an event to everybody who asked for it.
     *
     * <p>Safe to call from any thread, including a tick thread: the work is rendering one string and writing it to
     * whatever sockets are open, with no lock held across anything that could block for long.
     *
     * @param name the event name, as {@code context.thing}
     * @param data what the event carries, or an empty object when it carries nothing
     */
    public void publish(String name, JsonObject data) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(data, "data");
        if (live.isEmpty()) {
            return;
        }
        String message = null;
        for (EventSocket subscriber : live) {
            if (!subscriber.wants(name)) {
                continue;
            }
            if (message == null) {
                message = Json.write(envelope(name, data));
            }
            subscriber.deliver(message);
        }
    }

    /**
     * Whether anybody is listening for an event by this name.
     *
     * <p>Asked before an event is rendered, so a server with nothing subscribed pays a lookup per event and not a
     * payload per event.
     */
    public boolean wanted(String name) {
        for (EventSocket subscriber : live) {
            if (subscriber.wants(name)) {
                return true;
            }
        }
        return false;
    }

    /** How many connections are open, which is what {@code /uxmapi} reports. */
    public int subscribers() {
        return live.size();
    }

    private static JsonObject envelope(String name, JsonObject data) {
        JsonObject json = new JsonObject();
        json.addProperty("event", name);
        json.add("data", data);
        return json;
    }

    /** Say goodbye to everybody, which is what a listener being shut down owes its subscribers. */
    @Override
    public void close() {
        live.forEach(subscriber -> subscriber.closeWith(Frame.CLOSE_GOING_AWAY, "the server is shutting down"));
        live.clear();
    }
}
