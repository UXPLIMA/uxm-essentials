package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * Holds the predicates a spec can name to gate an item's visibility ({@code view}) or a click. A duplicate id is
 * a wiring mistake, so registration fails loudly rather than letting one condition shadow another.
 */
public final class ConditionRegistry {

    private final ConcurrentHashMap<String, Predicate<MenuContext>> handlers = new ConcurrentHashMap<>();

    public void register(String id, Predicate<MenuContext> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("condition already registered: " + id);
        }
    }

    public Optional<Predicate<MenuContext>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(handlers.get(id));
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        return handlers.containsKey(id);
    }
}
