package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;

/**
 * A sanction history kept in memory, newest last, that also says how many rows each rollback read asked for and can
 * be made to fail every write, as a database that is down would.
 */
final class RecordedHistory implements SanctionHistory {

    private final List<SanctionHistoryEntry> entries = new ArrayList<>();
    private final List<Integer> revocableReadLimits = new ArrayList<>();
    private int batches;
    private boolean down;

    /** Every row appended, oldest first. */
    List<SanctionHistoryEntry> entries() {
        return List.copyOf(entries);
    }

    /** How many {@link #appendAll} batches were written. */
    int batches() {
        return batches;
    }

    /** The limit of each {@link #revocableByActor} read, in order. */
    List<Integer> revocableReadLimits() {
        return List.copyOf(revocableReadLimits);
    }

    /** Every later write throws, as when the database cannot be reached. */
    void goDown() {
        down = true;
    }

    @Override
    public void append(SanctionHistoryEntry entry) {
        failIfDown();
        entries.add(entry);
    }

    @Override
    public void appendAll(List<SanctionHistoryEntry> batch) {
        failIfDown();
        entries.addAll(batch);
        batches++;
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
        return entries.reversed().stream()
                .filter(entry -> entry.actor().uuid().equals(Optional.of(actor)))
                .limit(limit)
                .toList();
    }

    @Override
    public List<SanctionHistoryEntry> revocableByActor(UUID actor, int limit) {
        revocableReadLimits.add(limit);
        return entries.reversed().stream()
                .filter(entry -> entry.actor().uuid().equals(Optional.of(actor)))
                .filter(entry -> entry.ip().isEmpty())
                .filter(entry -> entry.action() == SanctionAction.BAN
                        || entry.action() == SanctionAction.MUTE
                        || entry.action() == SanctionAction.WARN)
                .limit(limit)
                .toList();
    }

    @Override
    public List<SanctionHistory.Tally> countsSince(Instant threshold, Optional<UUID> actor) {
        return List.of();
    }

    private void failIfDown() {
        if (down) {
            throw new IllegalStateException("the database is down");
        }
    }
}
