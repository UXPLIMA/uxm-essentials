package com.uxplima.uxmessentials.persistence.teleport;

import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jspecify.annotations.Nullable;

/**
 * Complete durable spawn state loaded once before command/event readers become active. The main spawns are keyed
 * by their stored scope, the network-wide one under {@link #NETWORK}.
 */
record SpawnDirectorySnapshot(
        Map<UUID, Position> worlds,
        Map<String, Position> named,
        Map<UUID, SpawnMirror> mirrors,
        Map<String, Position> mains) {

    /** The key of the network-wide main spawn, which has no scope. */
    static final String NETWORK = "";

    SpawnDirectorySnapshot {
        worlds = Map.copyOf(worlds);
        named = Map.copyOf(named);
        mirrors = Map.copyOf(mirrors);
        mains = Map.copyOf(mains);
    }

    /** The key a main spawn of {@code scope} is held under. */
    static String mainKey(@Nullable ServerScope scope) {
        return scope == null ? NETWORK : scope.value();
    }
}
