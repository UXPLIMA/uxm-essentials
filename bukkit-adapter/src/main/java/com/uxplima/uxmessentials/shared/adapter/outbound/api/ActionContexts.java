package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.jspecify.annotations.NullMarked;

/**
 * The write surfaces the published API hands out, one per context that has anything to offer.
 *
 * <p>The same arrangement as {@link QueryContexts}, with one difference: a context registers a factory rather than
 * a surface, because every action is attributed to the plugin that asked for it. The factory takes that plugin's
 * name, so two plugins moving money leave two distinguishable audit lines while sharing one implementation.
 *
 * <p><b>Ownership.</b> One instance is created at the top of wiring and handed both to the API front door and to
 * the contexts, since the front door is published before the contexts wire. Writes come only from the enable
 * thread during wiring; nothing is removed, and a reload builds a fresh instance.
 */
@NullMarked
public final class ActionContexts {

    private final Map<Class<?>, Function<String, ?>> surfaces = new ConcurrentHashMap<>();

    /** A bundle with every context absent, which is what a server with every module off would produce. */
    public static ActionContexts empty() {
        return new ActionContexts();
    }

    /**
     * Publish {@code factory} as the way to build the surface for {@code type}. Called once by each enabled
     * context as it wires; a second registration is a wiring mistake rather than a runtime condition.
     */
    public <T> ActionContexts register(Class<T> type, Function<String, T> factory) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        Function<String, ?> previous = surfaces.putIfAbsent(type, factory);
        if (previous != null) {
            throw new IllegalStateException("action surface already registered: " + type.getName());
        }
        return this;
    }

    /** The surface for {@code type} attributed to {@code source}, or empty when that context is switched off. */
    public <T> Optional<T> find(Class<T> type, String source) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Function<String, ?> factory = surfaces.get(type);
        return factory == null ? Optional.empty() : Optional.of(type.cast(factory.apply(source)));
    }
}
