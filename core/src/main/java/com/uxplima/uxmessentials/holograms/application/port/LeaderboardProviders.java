package com.uxplima.uxmessentials.holograms.application.port;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NullMarked;

/**
 * The registry of {@link LeaderboardProvider}s by their lowercase id ({@code balance}, …). Built once by the
 * adapter wiring with the providers the server's enabled modules supply, and consulted by the renderer to resolve
 * a leaderboard hologram's stored provider id. An unknown id resolves to {@link Optional#empty()} so a leaderboard
 * whose provider's module was disabled simply renders nothing rather than throwing.
 */
@NullMarked
public final class LeaderboardProviders {

    private final Map<String, LeaderboardProvider> byId;

    public LeaderboardProviders(Map<String, LeaderboardProvider> byId) {
        Objects.requireNonNull(byId, "byId");
        Map<String, LeaderboardProvider> copy = new LinkedHashMap<>();
        byId.forEach((id, provider) -> copy.put(
                Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT), Objects.requireNonNull(provider, id)));
        this.byId = Collections.unmodifiableMap(copy);
    }

    /** The provider registered under {@code id} (case-insensitive), or empty when none is. */
    public Optional<LeaderboardProvider> find(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    /** The registered provider ids, for command tab-completion and validation. */
    public Set<String> ids() {
        return byId.keySet();
    }
}
