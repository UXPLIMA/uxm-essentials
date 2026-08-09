package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * The read surfaces the published API hands out, one per context that has anything to answer.
 *
 * <p>Every entry starts absent and is filled while that context wires. A module the operator switched off wires
 * nothing, so its entry stays absent and the front door answers {@link Optional#empty()} with no special case
 * anywhere: the same mechanism that makes a disabled module cost nothing makes it invisible to the API.
 *
 * <p>Absent is a real answer and not a failure. Nine modules ship switched off, and "homes are disabled on this
 * server" is something a consumer can act on, while an empty list of homes is not.
 *
 * <p><b>Ownership.</b> One instance is created at the top of wiring and handed both to the API front door and to the
 * contexts, because the front door is published to other plugins before the contexts have wired. Registration
 * therefore happens after publication, and the map is concurrent so a consumer reading from any thread sees what
 * wiring wrote. Writes come only from the enable thread and only during wiring; nothing removes an entry, and a
 * reload builds a fresh instance along with a fresh front door.
 */
@NullMarked
public final class QueryContexts {

    private final Map<Class<?>, Object> surfaces = new ConcurrentHashMap<>();

    /** A bundle with every context absent, which is what a server with every module off would produce. */
    public static QueryContexts empty() {
        return new QueryContexts();
    }

    /**
     * Publish {@code surface} as the answer for {@code type}. Called once by each enabled context as it wires; a
     * second registration for the same type is a wiring mistake rather than a runtime condition, so it is refused.
     */
    public <T> QueryContexts register(Class<T> type, T surface) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(surface, "surface");
        Object previous = surfaces.putIfAbsent(type, surface);
        if (previous != null) {
            throw new IllegalStateException("query surface already registered: " + type.getName());
        }
        return this;
    }

    /** The surface for {@code type}, or empty when the context that would answer it is switched off. */
    public <T> Optional<T> find(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return Optional.ofNullable(type.cast(surfaces.get(type)));
    }
}
