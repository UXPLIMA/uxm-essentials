package com.uxplima.uxmessentials.rest.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import com.uxplima.uxmessentials.rest.socket.EventStream;

/**
 * What turns a Bukkit event into a line on a socket.
 *
 * <p>One listener per published event class, registered at {@code MONITOR}: this only watches, and watching last
 * means what goes out is what everything else has already agreed on.
 *
 * <p>Nothing is written to a socket on a tick thread. The event is rendered where it arrives, which is a handful of
 * getter calls and no I/O, and the finished text is handed to one thread that does the writing. One thread rather
 * than many, so subscribers see events in the order the server raised them.
 *
 * <p>The queue between the two is bounded. A subscriber that has stopped reading must not be able to grow the
 * server's heap: past the bound the oldest waiting events are dropped and the fact is logged once, which is a
 * gap somebody can see rather than an outage they have to explain afterwards.
 */
public final class EventBridge implements Listener, AutoCloseable {

    /** How many rendered events may be waiting to go out before the oldest are dropped. */
    static final int QUEUE_LIMIT = 10_000;

    private final EventStream stream;
    private final Logger log;
    private final Map<Class<? extends UxmEvent>, String> names = new HashMap<>();
    private final BlockingQueue<Pending> waiting = new ArrayBlockingQueue<>(QUEUE_LIMIT);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean warned = new AtomicBoolean();
    private final Thread sender;

    public EventBridge(EventStream stream, Logger log) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.log = Objects.requireNonNull(log, "log");
        PublishedEvents.ALL.forEach(type -> names.put(type, EventNames.of(type)));
        this.sender = Thread.ofVirtual().name("uxmessentials-rest-events").unstarted(this::send);
    }

    /** Start listening, which is the only thing that makes any of the above happen. */
    public void register(Plugin plugin, PluginManager plugins) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(plugins, "plugins");
        sender.start();
        names.forEach((type, name) -> plugins.registerEvent(
                type,
                this,
                EventPriority.MONITOR,
                (listener, event) -> {
                    if (type.isInstance(event)) {
                        offer(name, type.cast(event));
                    }
                },
                plugin,
                true));
        log.log(Level.FINE, "the event stream is watching {0} kinds of event", names.size());
    }

    /**
     * Render and queue an event, or do nothing at all when nobody asked for it.
     *
     * <p>The check comes first on purpose. With no subscriber for a name this costs one lookup, which is what a
     * listener on a hot path is allowed to cost.
     */
    private void offer(String name, UxmEvent event) {
        if (!stream.wanted(name)) {
            return;
        }
        JsonObject data = EventJson.of(event);
        if (!waiting.offer(new Pending(name, data))) {
            waiting.poll();
            waiting.offer(new Pending(name, data));
            if (warned.compareAndSet(false, true)) {
                log.warning("The event stream is further behind than " + QUEUE_LIMIT
                        + " events and is dropping the oldest. A subscriber is not reading fast enough.");
            }
        }
    }

    private void send() {
        while (running.get()) {
            try {
                Pending next = waiting.take();
                stream.publish(next.name(), next.data());
            } catch (InterruptedException stopping) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException failure) {
                // One event that could not be written is not a reason to stop writing the rest of them.
                log.log(Level.WARNING, "an event could not be sent to the stream", failure);
            }
        }
    }

    /** Stop sending. The listeners go with the plugin, which is what unregisters them. */
    @Override
    public void close() {
        running.set(false);
        sender.interrupt();
    }

    /** An event that has been rendered and is waiting its turn on the wire. */
    private record Pending(String name, JsonObject data) {}
}
