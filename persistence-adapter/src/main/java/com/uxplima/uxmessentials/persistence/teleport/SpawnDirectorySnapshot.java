package com.uxplima.uxmessentials.persistence.teleport;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;

/** Complete durable spawn state loaded once before command/event readers become active. */
record SpawnDirectorySnapshot(
        Map<UUID, Position> worlds,
        Map<String, Position> named,
        Map<UUID, SpawnMirror> mirrors,
        Optional<Position> main) {

    SpawnDirectorySnapshot {
        worlds = Map.copyOf(worlds);
        named = Map.copyOf(named);
        mirrors = Map.copyOf(mirrors);
    }
}
