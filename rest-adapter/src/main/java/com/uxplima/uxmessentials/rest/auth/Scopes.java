package com.uxplima.uxmessentials.rest.auth;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a token is allowed to do.
 *
 * <p>Three of them, because the question worth asking is what kind of thing a caller does rather than which
 * endpoints it happens to call. A panel that only draws graphs gets {@code read} and cannot move anybody's money
 * even if somebody later adds an endpoint that would.
 */
public final class Scopes {

    /** Every {@code GET}. */
    public static final String READ = "read";

    /** Every {@code POST}. */
    public static final String WRITE = "write";

    /** The event stream. */
    public static final String EVENTS = "events";

    /** All three, in the order they are printed. */
    public static final Set<String> ALL = Set.of(READ, WRITE, EVENTS);

    private Scopes() {}

    /**
     * Read a comma-separated list, keeping only the scopes that exist.
     *
     * @throws IllegalArgumentException when nothing in the list is a scope, since a token with no scope could do
     *     nothing and silently issuing one would be worse than refusing
     */
    public static Set<String> parse(String raw) {
        Set<String> parsed = new TreeSet<>();
        for (String part : raw.split(",", -1)) {
            String scope = part.trim().toLowerCase(Locale.ROOT);
            if (ALL.contains(scope)) {
                parsed.add(scope);
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("no scope in that list: " + raw);
        }
        return Set.copyOf(parsed);
    }
}
