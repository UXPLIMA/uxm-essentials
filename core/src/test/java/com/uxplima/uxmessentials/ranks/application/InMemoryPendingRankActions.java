package com.uxplima.uxmessentials.ranks.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.port.PendingRankActions;

/** The kept-actions store in memory, one list per player in the order the lines were kept. */
final class InMemoryPendingRankActions implements PendingRankActions {

    private final Map<UUID, List<String>> kept = new HashMap<>();
    private boolean failing;

    @Override
    public void keep(UUID player, List<String> lines) {
        if (failing) {
            throw new IllegalStateException("database down");
        }
        kept.computeIfAbsent(player, id -> new ArrayList<>()).addAll(lines);
    }

    @Override
    public List<String> take(UUID player) {
        if (failing) {
            throw new IllegalStateException("database down");
        }
        List<String> lines = kept.remove(player);
        return lines == null ? List.of() : List.copyOf(lines);
    }

    /** What is kept for {@code player} right now. */
    List<String> keptFor(UUID player) {
        return List.copyOf(kept.getOrDefault(player, List.of()));
    }

    /** Make every read and write throw, as a database that is down does. */
    void fail() {
        failing = true;
    }
}
