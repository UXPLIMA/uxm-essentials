package com.uxplima.uxmessentials.economy.application.port;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The closed set of backends the server has, keyed by {@link CurrencyBackend#id()}. Built once at module start
 * and never mutated, mirroring {@code CurrencyRegistry}: an unknown id is an error surfaced to the caller, not
 * a silent fall-back to the native ledger, because paying a warp fee out of the wrong economy is worse than
 * refusing to start.
 */
public final class CurrencyBackendRegistry {

    private final Map<String, CurrencyBackend> byId;

    private CurrencyBackendRegistry(Map<String, CurrencyBackend> byId) {
        // A LinkedHashMap copy, not Map.copyOf: the latter randomises iteration order per JVM run, which would
        // break the registration-order guarantee ids() makes and the operator reads in the startup error.
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    /** Build the registry; two backends claiming one id is a programming error, not a precedence question. */
    public static CurrencyBackendRegistry of(Collection<CurrencyBackend> backends) {
        Objects.requireNonNull(backends, "backends");
        Map<String, CurrencyBackend> map = new LinkedHashMap<>();
        for (CurrencyBackend backend : backends) {
            CurrencyBackend previous = map.put(backend.id(), backend);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate currency backend id: " + backend.id());
            }
        }
        return new CurrencyBackendRegistry(map);
    }

    /** The backend registered under {@code id}, or empty. */
    public Optional<CurrencyBackend> find(String id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }

    /** Every registered backend id, in registration order. */
    public Set<String> ids() {
        return byId.keySet();
    }
}
