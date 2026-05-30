package com.uxplima.uxmessentials.bootstrap.di;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * A simple immutable-map-backed {@link ConfigStore}.
 *
 * <p>This phase has no HOCON config file yet; the store is constructed from an explicit map so the
 * module enable gates and the {@code /uxmess} surface have a real config port to read. When the
 * Configurate-backed loader lands it replaces this implementation behind the same interface — the
 * gated wiring loop and the registry never change. A path that is absent yields the caller's
 * fallback, which is how a module's {@code enabled} flag defaults to {@code true}.
 */
@NullMarked
public final class BootstrapConfigStore implements ConfigStore {

    private final Map<String, Object> values;

    public BootstrapConfigStore(Map<String, Object> values) {
        this.values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    /** A store with no overrides, so every lookup returns the caller's fallback. */
    public static BootstrapConfigStore empty() {
        return new BootstrapConfigStore(Map.of());
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        Object value = values.get(Objects.requireNonNull(path, "path"));
        return value instanceof Boolean b ? b : fallback;
    }

    @Override
    public String getString(String path, String fallback) {
        Object value = values.get(Objects.requireNonNull(path, "path"));
        return value instanceof String s ? s : fallback;
    }

    @Override
    public int getInt(String path, int fallback) {
        Object value = values.get(Objects.requireNonNull(path, "path"));
        return value instanceof Integer i ? i : fallback;
    }
}
