package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;

/** A sanction history that keeps nothing; the history is not what these tests look at. */
final class DiscardedHistory implements SanctionHistory {

    @Override
    public void append(SanctionHistoryEntry entry) {
        // Nothing is kept.
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
        return List.of();
    }

    @Override
    public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
        return List.of();
    }

    @Override
    public List<SanctionHistoryEntry> revocableByActor(UUID actor, int limit) {
        return List.of();
    }

    @Override
    public List<SanctionHistoryEntry> allSince(Instant threshold, int limit) {
        return List.of();
    }
}
