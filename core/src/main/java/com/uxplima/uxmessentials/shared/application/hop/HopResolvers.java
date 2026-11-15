package com.uxplima.uxmessentials.shared.application.hop;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The resolvers this backend knows, one per {@link HopKind}. Contexts register theirs while the plugin wires, and
 * the hop reads them from the async lane, hence the concurrent map. A kind with no resolver here is one this
 * backend can neither move nor refund, so the hop leaves such a row for a backend that can.
 */
public final class HopResolvers {

    private final Map<HopKind, HopResolver> byKind = new ConcurrentHashMap<>();

    /** Register {@code resolver} for its kind; a second resolver for the same kind is a wiring fault. */
    public void register(HopResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        HopResolver earlier = byKind.putIfAbsent(resolver.kind(), resolver);
        if (earlier != null && earlier != resolver) {
            throw new IllegalStateException("a resolver for " + resolver.kind() + " is already registered");
        }
    }

    /** The resolver for {@code kind}, if one is registered on this backend. */
    public Optional<HopResolver> find(HopKind kind) {
        Objects.requireNonNull(kind, "kind");
        return Optional.ofNullable(byKind.get(kind));
    }
}
