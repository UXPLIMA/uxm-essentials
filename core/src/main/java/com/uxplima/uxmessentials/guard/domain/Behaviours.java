package com.uxplima.uxmessentials.guard.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A region's behaviour values, already normalised by {@link BehaviourKey#normalise(String)}. Immutable: every
 * change returns a new instance.
 */
public final class Behaviours {

    private static final Behaviours EMPTY = new Behaviours(new EnumMap<>(BehaviourKey.class));

    private final EnumMap<BehaviourKey, String> values;

    private Behaviours(EnumMap<BehaviourKey, String> values) {
        this.values = values;
    }

    public static Behaviours empty() {
        return EMPTY;
    }

    /** Behaviours from already normalised values; an entry that does not normalise is dropped. */
    public static Behaviours of(Map<BehaviourKey, String> raw) {
        EnumMap<BehaviourKey, String> copy = new EnumMap<>(BehaviourKey.class);
        raw.forEach((key, value) -> key.normalise(value).ifPresent(v -> copy.put(key, v)));
        return copy.isEmpty() ? EMPTY : new Behaviours(copy);
    }

    public Optional<String> get(BehaviourKey key) {
        return Optional.ofNullable(values.get(key));
    }

    public boolean has(BehaviourKey key) {
        return values.containsKey(key);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** This set with {@code key} set to {@code value}; throws when the value does not fit the key. */
    public Behaviours with(BehaviourKey key, String value) {
        String normal = key.normalise(value)
                .orElseThrow(() -> new IllegalArgumentException("invalid value for " + key.key() + ": " + value));
        EnumMap<BehaviourKey, String> copy = new EnumMap<>(BehaviourKey.class);
        copy.putAll(values);
        copy.put(key, normal);
        return new Behaviours(copy);
    }

    /** This set without {@code key}. */
    public Behaviours without(BehaviourKey key) {
        if (!values.containsKey(key)) {
            return this;
        }
        EnumMap<BehaviourKey, String> copy = new EnumMap<>(values);
        copy.remove(key);
        return copy.isEmpty() ? EMPTY : new Behaviours(copy);
    }

    /** {@code overrides} on top of this set: a key set in both takes the override's value. */
    public Behaviours overlay(Behaviours overrides) {
        if (overrides.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return overrides;
        }
        EnumMap<BehaviourKey, String> copy = new EnumMap<>(values);
        copy.putAll(overrides.values);
        return new Behaviours(copy);
    }

    /** A read-only view of the values. */
    public Map<BehaviourKey, String> asMap() {
        return java.util.Collections.unmodifiableMap(values);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Behaviours other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
