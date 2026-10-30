package com.uxplima.uxmessentials.playerstate.application;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;

/** A playtime ledger in memory. Days are not kept apart: every sample counts toward the all-time totals only. */
final class InMemoryPlaytime implements PlaytimeRepository {

    private final Map<UUID, long[]> counted = new HashMap<>();
    private final Map<UUID, Long> corrections = new HashMap<>();
    private final Set<UUID> imported = new HashSet<>();

    @Override
    public void addSeconds(UUID uuid, LocalDate day, long activeDelta, long afkDelta) {
        long[] row = counted.computeIfAbsent(uuid, ignored -> new long[2]);
        row[0] += activeDelta;
        row[1] += afkDelta;
    }

    @Override
    public PlaytimeSummary summaryOf(UUID uuid, LocalDate today) {
        long[] row = counted.getOrDefault(uuid, new long[2]);
        return PlaytimeSummary.ofSeconds(0, 0, 0, 0, 0, 0, row[0], row[1])
                .withAdjustment(corrections.getOrDefault(uuid, 0L));
    }

    @Override
    public void adjust(UUID uuid, long deltaSeconds) {
        corrections.merge(uuid, deltaSeconds, Long::sum);
    }

    @Override
    public boolean imported(UUID uuid) {
        return imported.contains(uuid);
    }

    @Override
    public void markImported(UUID uuid) {
        imported.add(uuid);
    }

    @Override
    public void reset(UUID uuid) {
        counted.remove(uuid);
        corrections.remove(uuid);
    }

    @Override
    public void resetAll() {
        counted.clear();
        corrections.clear();
    }
}
