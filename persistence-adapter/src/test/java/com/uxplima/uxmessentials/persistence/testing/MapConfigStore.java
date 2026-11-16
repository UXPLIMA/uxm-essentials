package com.uxplima.uxmessentials.persistence.testing;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/** A config holding a few keys, for the {@code storage} block a test database is opened with. */
final class MapConfigStore implements ConfigStore {

    private final Map<String, Object> values;

    MapConfigStore(Map<String, Object> values) {
        this.values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        Object value = values.get(path);
        return value instanceof Boolean flag ? flag : fallback;
    }

    @Override
    public String getString(String path, String fallback) {
        Object value = values.get(path);
        return value == null ? fallback : value.toString();
    }

    @Override
    public int getInt(String path, int fallback) {
        Object value = values.get(path);
        return value instanceof Integer number ? number : fallback;
    }
}
