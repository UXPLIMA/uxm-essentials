package com.uxplima.uxmessentials.worlds.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The per-world settings bag: an immutable map of {@code setting_key → setting_value} rows backing a
 * world. Scalar properties are typed via the {@link WorldProperty} catalog; gamerules live under the
 * {@code gamerule.} key prefix; the world spawn lives under {@code spawn}. Mutations return a new
 * instance.
 */
public final class WorldSettings {

    private static final String GAMERULE_PREFIX = "gamerule.";
    private static final String SPAWN_KEY = "spawn";
    private static final WorldSettings DEFAULTS = new WorldSettings(Map.of());

    private final Map<String, String> raw;

    private WorldSettings(Map<String, String> raw) {
        this.raw = Map.copyOf(raw);
    }

    public static WorldSettings defaults() {
        return DEFAULTS;
    }

    public static WorldSettings fromRaw(Map<String, String> raw) {
        return new WorldSettings(Objects.requireNonNull(raw, "raw"));
    }

    public Map<String, String> raw() {
        return raw;
    }

    public <T> T get(WorldProperty<T> property) {
        Objects.requireNonNull(property, "property");
        String value = raw.get(property.key());
        return value == null ? property.defaultValue() : property.decode(value).orElse(property.defaultValue());
    }

    public <T> WorldSettings with(WorldProperty<T> property, T value) {
        Objects.requireNonNull(property, "property");
        return withRaw(property.key(), property.encode(value));
    }

    public WorldSettings withRaw(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String, String> next = new LinkedHashMap<>(raw);
        next.put(key, value);
        return new WorldSettings(next);
    }

    public WorldSettings withoutRaw(String key) {
        Objects.requireNonNull(key, "key");
        if (!raw.containsKey(key)) {
            return this;
        }
        Map<String, String> next = new LinkedHashMap<>(raw);
        next.remove(key);
        return new WorldSettings(next);
    }

    public Optional<String> rawValue(String key) {
        return Optional.ofNullable(raw.get(Objects.requireNonNull(key, "key")));
    }

    public Map<String, String> gamerules() {
        Map<String, String> rules = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key.startsWith(GAMERULE_PREFIX)) {
                rules.put(key.substring(GAMERULE_PREFIX.length()), value);
            }
        });
        return Map.copyOf(rules);
    }

    public static String gameruleKey(String rule) {
        return GAMERULE_PREFIX + Objects.requireNonNull(rule, "rule");
    }

    public Optional<String> spawn() {
        return rawValue(SPAWN_KEY);
    }

    public static String spawnKey() {
        return SPAWN_KEY;
    }
}
