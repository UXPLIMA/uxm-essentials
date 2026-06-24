package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.HashMap;
import java.util.Map;
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

    /**
     * Resolve every registered placeholder against {@code ctx} into an {@code id -> value} map, so a catalog
     * {@code @key} text can fill its {@code {token}} arguments from the same placeholders a {@code %token%}
     * spec uses. A resolver that throws because this context does not carry what it needs (a placeholder owned
     * by a different menu) is skipped rather than aborting the render — the token it would fill simply stays
     * unresolved, the same fail-soft stance the rest of the renderer takes.
     */
    public Map<String, String> resolveAll(MenuContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Map<String, String> resolved = new HashMap<>();
        handlers.forEach((id, handler) -> {
            try {
                String value = handler.apply(ctx);
                if (value != null) {
                    resolved.put(id, value);
                }
            } catch (RuntimeException notApplicableHere) {
                // This placeholder belongs to a context shape this menu does not have; leave its token unfilled.
            }
        });
        return resolved;
    }
}
