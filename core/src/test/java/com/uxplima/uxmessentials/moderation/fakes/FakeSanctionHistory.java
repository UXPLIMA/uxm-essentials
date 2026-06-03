package com.uxplima.uxmessentials.moderation.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;

/**
 * An in-memory {@link SanctionHistory} for the application tests: an append-only list a test reads back to
 * assert exactly which rows a use case recorded. The two read methods filter by family and return newest-first
 * and capped, mirroring the jOOQ adapter's observable behaviour so a test asserts the same shape without a DB.
 */
public final class FakeSanctionHistory implements SanctionHistory {

    public final List<SanctionHistoryEntry> appended = new ArrayList<>();

    @Override
    public void append(SanctionHistoryEntry entry) {
        appended.add(entry);
    }

    @Override
    public List<SanctionHistoryEntry> banHistory(UUID target, int limit) {
        return scoped(target, limit, SanctionAction.BAN, SanctionAction.UNBAN);
    }

    @Override
    public List<SanctionHistoryEntry> muteHistory(UUID target, int limit) {
        return scoped(target, limit, SanctionAction.MUTE, SanctionAction.UNMUTE);
    }

    private List<SanctionHistoryEntry> scoped(UUID target, int limit, SanctionAction a, SanctionAction b) {
        List<SanctionHistoryEntry> rows = new ArrayList<>(appended.stream()
                .filter(e -> e.target().equals(target))
                .filter(e -> e.action() == a || e.action() == b)
                .toList());
        java.util.Collections.reverse(rows);
        return rows.stream().limit(Math.max(0, limit)).toList();
    }
}
