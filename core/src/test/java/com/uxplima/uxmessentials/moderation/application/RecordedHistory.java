package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;

/** A sanction history kept in memory, newest last, that also says how many rows each staff read asked for. */
final class RecordedHistory implements SanctionHistory {

    private final List<SanctionHistoryEntry> entries = new ArrayList<>();
    private final List<Integer> actorReadLimits = new ArrayList<>();

    /** Every row appended, oldest first. */
    List<SanctionHistoryEntry> entries() {
        return List.copyOf(entries);
    }

    /** The limit of each {@link #recentByActor} read, in order. */
    List<Integer> actorReadLimits() {
        return List.copyOf(actorReadLimits);
    }

    @Override
    public void append(SanctionHistoryEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<SanctionHistoryEntry> banHistory(UUID target, int limit) {
        return List.of();
    }

    @Override
    public List<SanctionHistoryEntry> muteHistory(UUID target, int limit) {
        return List.of();
    }

    @Override
    public List<SanctionHistoryEntry> recentForTarget(UUID target, int limit) {
        return entries.reversed().stream()
                .filter(entry -> entry.target().equals(target))
                .limit(limit)
                .toList();
    }

    @Override
    public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
        actorReadLimits.add(limit);
        return entries.reversed().stream()
                .filter(entry -> entry.actor().uuid().equals(Optional.of(actor)))
                .limit(limit)
                .toList();
    }

    @Override
    public List<SanctionHistoryEntry> allSince(Instant threshold, int limit) {
        return List.copyOf(entries);
    }
}
