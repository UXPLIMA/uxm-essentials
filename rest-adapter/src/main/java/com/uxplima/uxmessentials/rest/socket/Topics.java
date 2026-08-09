package com.uxplima.uxmessentials.rest.socket;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * What one subscriber has asked to hear.
 *
 * <p>Three forms, in the order somebody reaches for them: {@code *} for everything, {@code economy.*} for one
 * context, and {@code economy.wallet-credit} for one event. A prefix ending in a star is the whole of the pattern
 * language, because event names are already {@code context.thing} and anything richer would be a query language
 * nobody asked for.
 *
 * <p>A connection with nothing subscribed hears nothing. Silence is the honest default: a panel that forgot to
 * subscribe and is quietly handed every event on the server is a bandwidth bill rather than a feature.
 */
public final class Topics {

    /** Every event there is. */
    private static final String EVERYTHING = "*";

    private final Set<String> patterns = new CopyOnWriteArraySet<>();

    /** Start listening to these. */
    public void add(Collection<String> wanted) {
        wanted.stream().map(Topics::clean).filter(pattern -> !pattern.isEmpty()).forEach(patterns::add);
    }

    /** Stop listening to these, matched as they were written. */
    public void remove(Collection<String> unwanted) {
        unwanted.stream().map(Topics::clean).forEach(patterns::remove);
    }

    /** Everything currently subscribed, for the acknowledgement that goes back after a change. */
    public Set<String> current() {
        return Set.copyOf(patterns);
    }

    /** Whether an event by this name should reach this subscriber. */
    public boolean wants(String eventName) {
        Objects.requireNonNull(eventName, "eventName");
        for (String pattern : patterns) {
            if (matches(pattern, eventName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String pattern, String eventName) {
        if (EVERYTHING.equals(pattern)) {
            return true;
        }
        if (pattern.endsWith(EVERYTHING)) {
            return eventName.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(eventName);
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
