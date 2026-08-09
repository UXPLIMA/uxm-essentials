package com.uxplima.uxmessentials.rest.socket;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.rest.http.Json;

/**
 * One connected subscriber: the socket, what it has asked to hear, and the loop that reads from it.
 *
 * <p>Traffic goes both ways at once. The connection's own thread sits in {@link #listen()} waiting for a frame,
 * while events arrive from whatever thread the server raised them on and are written by {@link #deliver}. The two
 * never contend for anything but the write lock, which is held only for as long as one frame takes to put on the
 * wire.
 *
 * <p>The read has a timeout, and the timeout is the keepalive: a stream with nothing to say for a minute gets a
 * ping, and a connection that has quietly gone (a laptop closed, a container killed) fails on that write rather
 * than sitting in the live set forever holding a thread.
 */
public final class EventSocket {

    /** How long a subscriber may say nothing before it is pinged. */
    static final int IDLE_MILLIS = 60_000;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final String caller;
    private final Topics topics = new Topics();
    private final ReentrantLock writing = new ReentrantLock();
    private final AtomicBoolean open = new AtomicBoolean(true);

    EventSocket(Socket socket, InputStream in, OutputStream out, String caller) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
        this.caller = Objects.requireNonNull(caller, "caller");
    }

    /** The label of the token behind this connection, which is what an operator sees in the log. */
    public String caller() {
        return caller;
    }

    /** Whether this subscriber asked for events by this name. */
    public boolean wants(String eventName) {
        return open.get() && topics.wants(eventName);
    }

    /** Send an already-rendered event, quietly dropping the connection if it has gone. */
    public void deliver(String message) {
        if (!open.get()) {
            return;
        }
        try {
            write(Frame.text(message));
        } catch (IOException gone) {
            open.set(false);
        }
    }

    /**
     * Read from this connection until it ends.
     *
     * <p>Returns when the client closes, the connection breaks, or the client breaks the protocol. Every one of
     * those is an ordinary end to a stream rather than an error worth a stack trace, so nothing is thrown out of
     * here: the caller's job afterwards is only to take this out of the live set.
     */
    void listen() {
        try {
            socket.setSoTimeout(IDLE_MILLIS);
            write(Frame.text(Json.write(hello())));
            loop();
        } catch (Frame.ProtocolException broken) {
            closeWith(broken.closeCode(), String.valueOf(broken.getMessage()));
        } catch (IOException ended) {
            open.set(false);
        }
    }

    private void loop() throws IOException {
        while (open.get()) {
            Frame frame;
            try {
                frame = Frame.read(in);
            } catch (SocketTimeoutException quiet) {
                write(Frame.ping());
                continue;
            } catch (EOFException ended) {
                open.set(false);
                return;
            }
            handle(frame);
        }
    }

    private void handle(Frame frame) throws IOException {
        switch (frame.opcode()) {
            case Frame.TEXT -> write(Frame.text(Json.write(answer(frame.text()))));
            case Frame.PING -> write(Frame.pong(frame.payload()));
            case Frame.PONG -> {
                // Nothing to do: the point of a pong is that the write to send it worked.
            }
            case Frame.CLOSE -> closeWith(Frame.CLOSE_GOING_AWAY, "goodbye");
            default ->
                throw new Frame.ProtocolException(
                        Frame.CLOSE_UNSUPPORTED, "this listener reads text, ping, pong and close");
        }
    }

    /** What to say back to a line the client sent. */
    private JsonObject answer(String line) {
        JsonObject command;
        try {
            command = Json.parse(line);
        } catch (RuntimeException notJson) {
            return problem("send a JSON object: {\"subscribe\":[\"economy.*\"]}");
        }
        boolean changed = false;
        if (command.has("subscribe")) {
            topics.add(names(command.get("subscribe")));
            changed = true;
        }
        if (command.has("unsubscribe")) {
            topics.remove(names(command.get("unsubscribe")));
            changed = true;
        }
        if (!changed) {
            return problem("nothing to do: send subscribe, unsubscribe, or both");
        }
        return subscribed();
    }

    /** A name or a list of them, since one of each is what people write. */
    private static List<String> names(JsonElement element) {
        List<String> names = new ArrayList<>();
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            array.forEach(entry -> {
                if (entry.isJsonPrimitive()) {
                    names.add(entry.getAsString());
                }
            });
        } else if (element.isJsonPrimitive()) {
            names.add(element.getAsString());
        }
        return names;
    }

    private JsonObject hello() {
        JsonObject json = new JsonObject();
        json.addProperty("event", "connected");
        json.addProperty("caller", caller);
        json.addProperty(
                "message", "subscribe to start: {\"subscribe\":[\"*\"]} for everything, or one context at a time");
        return json;
    }

    private JsonObject subscribed() {
        JsonObject json = new JsonObject();
        json.addProperty("event", "subscribed");
        JsonArray current = new JsonArray();
        topics.current().stream().sorted().forEach(current::add);
        json.add("topics", current);
        return json;
    }

    private static JsonObject problem(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("event", "error");
        json.addProperty("message", message);
        return json;
    }

    /** Say why, then hang up. Both halves are best-effort: the connection may already have gone. */
    void closeWith(int code, String reason) {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        try {
            write(Frame.close(code, reason));
        } catch (IOException alreadyGone) {
            // Nothing to say to a socket that has already closed, and nobody to say it to.
        }
        try {
            socket.close();
        } catch (IOException alreadyClosed) {
            // Same: the connection is over either way, which is all this method was for.
        }
    }

    private void write(byte[] frame) throws IOException {
        writing.lock();
        try {
            out.write(frame);
            out.flush();
        } finally {
            writing.unlock();
        }
    }
}
