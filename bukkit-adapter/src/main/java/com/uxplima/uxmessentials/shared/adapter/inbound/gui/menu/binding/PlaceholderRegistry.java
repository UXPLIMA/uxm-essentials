package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * Holds the functions that expand a spec's {@code %token%} placeholders into text at render time. A duplicate id
 * is a wiring mistake, so registration fails loudly rather than letting one token resolver overwrite another.
 */
public final class PlaceholderRegistry {

    private final ConcurrentHashMap<String, Function<MenuContext, String>> handlers = new ConcurrentHashMap<>();

    public void register(String id, Function<MenuContext, String> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("placeholder already registered: " + id);
        }
    }

    public Optional<Function<MenuContext, String>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(handlers.get(id));
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        return handlers.containsKey(id);
    }
}
