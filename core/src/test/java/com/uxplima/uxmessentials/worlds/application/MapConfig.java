package com.uxplima.uxmessentials.worlds.application;

import java.util.HashMap;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/** The worlds config as an operator wrote it: the keys given here, every other key at its default. */
final class MapConfig implements ConfigStore {

    private final Map<String, Object> values = new HashMap<>();

    MapConfig set(String path, Object value) {
        values.put(path, value);
        return this;
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        return values.get(path) instanceof Boolean flag ? flag : fallback;
    }

    @Override
    public String getString(String path, String fallback) {
        Object value = values.get(path);
        return value == null ? fallback : value.toString();
    }

    @Override
    public int getInt(String path, int fallback) {
        return values.get(path) instanceof Integer number ? number : fallback;
    }
}
