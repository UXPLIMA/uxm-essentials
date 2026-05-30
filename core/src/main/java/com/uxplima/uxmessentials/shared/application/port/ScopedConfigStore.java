package com.uxplima.uxmessentials.shared.application.port;

import java.util.List;
import java.util.Objects;

/**
 * A {@link ConfigStore} view rooted at a fixed prefix. Every lookup prepends {@code prefix + "."}
 * before delegating, so a module handed a scoped store reads {@code default-warmup} and the
 * delegate resolves {@code modules.teleport.default-warmup}. Reload and further scoping flow through
 * to the delegate, so the atomic-swap semantics are preserved through the view.
 */
final class ScopedConfigStore implements ConfigStore {

    private final ConfigStore delegate;
    private final String prefix;

    ScopedConfigStore(ConfigStore delegate, String prefix) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    private String absolute(String path) {
        return prefix + "." + Objects.requireNonNull(path, "path");
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        return delegate.getBoolean(absolute(path), fallback);
    }

    @Override
    public String getString(String path, String fallback) {
        return delegate.getString(absolute(path), fallback);
    }

    @Override
    public int getInt(String path, int fallback) {
        return delegate.getInt(absolute(path), fallback);
    }

    @Override
    public long getLong(String path, long fallback) {
        return delegate.getLong(absolute(path), fallback);
    }

    @Override
    public double getDouble(String path, double fallback) {
        return delegate.getDouble(absolute(path), fallback);
    }

    @Override
    public List<String> getStringList(String path, List<String> fallback) {
        return delegate.getStringList(absolute(path), fallback);
    }

    @Override
    public void reload() {
        delegate.reload();
    }

    @Override
    public ConfigStore scoped(String nested) {
        Objects.requireNonNull(nested, "nested");
        return nested.isEmpty() ? this : new ScopedConfigStore(delegate, absolute(nested));
    }
}
