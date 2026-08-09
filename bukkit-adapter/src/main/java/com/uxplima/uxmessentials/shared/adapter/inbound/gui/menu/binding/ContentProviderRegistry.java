package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;

/**
 * Holds the providers that fill and police a menu's {@code content {}} regions, keyed by the id a region names. A
 * duplicate id is a wiring mistake, so registration fails loudly rather than letting one provider shadow another:
 * the shadowed one would be the code enforcing a feature's item rules.
 */
public final class ContentProviderRegistry {

    private final ConcurrentHashMap<String, ContentProvider> providers = new ConcurrentHashMap<>();

    public void register(String id, ContentProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (providers.putIfAbsent(id, provider) != null) {
            throw new IllegalStateException("content provider already registered: " + id);
        }
    }

    public Optional<ContentProvider> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(providers.get(id));
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        return providers.containsKey(id);
    }

    /** Every registered content-provider id, sorted. */
    public List<String> ids() {
        return providers.keySet().stream().sorted().toList();
    }
}
