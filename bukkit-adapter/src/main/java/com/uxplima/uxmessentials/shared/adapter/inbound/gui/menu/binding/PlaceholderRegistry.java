package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * Holds the functions that expand a spec's {@code %token%} placeholders into text at render time. A duplicate id
 * is a wiring mistake, so registration fails loudly rather than letting one token resolver overwrite another.
 *
 * <p>Besides id-keyed handlers, the registry carries one optional {@link Fallback}: a resolver that claims a
 * family of ids by predicate rather than by exact name, so a whole prefix (the {@code papi_*} PlaceholderAPI
 * bridge) resolves without a handler per token. The fallback is consulted only when no exact handler matches,
 * and {@link #has} treats a claimed id as known so {@link MenuBindings#validate} accepts a {@code %papi_*%} spec.
 */
public final class PlaceholderRegistry {

    private final ConcurrentHashMap<String, Function<MenuContext, String>> handlers = new ConcurrentHashMap<>();

    /** The single prefix/family resolver consulted when no exact handler matches; {@code null} until one is set. */
    private final AtomicReference<Fallback> fallback = new AtomicReference<>();

    public void register(String id, Function<MenuContext, String> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("placeholder already registered: " + id);
        }
    }

    /**
     * Register the single fallback resolver. {@code claims} decides which ids the {@code resolve} function owns
     * (e.g. {@code id -> id.startsWith("papi_")}); {@code resolve} expands a claimed id against the open context.
     * Only one fallback is allowed — a second registration is a wiring mistake and fails loudly, matching how an
     * id-keyed handler refuses to be overwritten.
     */
    public void fallback(Predicate<String> claims, BiFunction<String, MenuContext, String> resolve) {
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(resolve, "resolve");
        if (!fallback.compareAndSet(null, new Fallback(claims, resolve))) {
            throw new IllegalStateException("placeholder fallback already registered");
        }
    }

    public Optional<Function<MenuContext, String>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(handlers.get(id));
    }

    /**
     * Resolve {@code id} against {@code ctx}: the exact handler if one is registered, else the fallback when it
     * claims the id, else empty. This is the single seam the renderer substitutes a {@code %token%} through, so a
     * {@code %papi_*%} token resolves through the fallback while a plain {@code %page%} resolves through its handler.
     */
    public Optional<String> resolve(String id, MenuContext ctx) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ctx, "ctx");
        Function<MenuContext, String> handler = handlers.get(id);
        if (handler != null) {
            return Optional.ofNullable(handler.apply(ctx));
        }
        Fallback active = fallback.get();
        if (active != null && active.claims().test(id)) {
            return Optional.ofNullable(active.resolve().apply(id, ctx));
        }
        return Optional.empty();
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        if (handlers.containsKey(id)) {
            return true;
        }
        Fallback active = fallback.get();
        return active != null && active.claims().test(id);
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

    /** The prefix/family fallback: a predicate over the ids it owns and the resolver that expands one of them. */
    private record Fallback(Predicate<String> claims, BiFunction<String, MenuContext, String> resolve) {}
}
