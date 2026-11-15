package com.uxplima.uxmessentials.testing;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHopStore;
import org.jspecify.annotations.Nullable;

/** The network-shared hop table: one row per player, deleted only while it is still the hop the caller read. */
public final class MemoryHopStore implements PendingHopStore {

    private final Map<UUID, PendingHop> rows = new HashMap<>();
    private @Nullable Runnable afterNextFind;
    private boolean recordFails;

    /** Run {@code racer} once right after the next read, so a second caller can act between a read and its delete. */
    public void afterNextFind(Runnable racer) {
        this.afterNextFind = racer;
    }

    public void failRecords(boolean fails) {
        this.recordFails = fails;
    }

    public Map<UUID, PendingHop> rows() {
        return Map.copyOf(rows);
    }

    @Override
    public void record(PendingHop pending) {
        if (recordFails) {
            throw new IllegalStateException("the database refused the write");
        }
        rows.put(pending.player(), pending);
    }

    @Override
    public Optional<PendingHop> find(UUID player) {
        Optional<PendingHop> found = Optional.ofNullable(rows.get(player));
        Runnable racer = afterNextFind;
        afterNextFind = null;
        if (racer != null) {
            racer.run();
        }
        return found;
    }

    @Override
    public int clear(PendingHop pending) {
        PendingHop stored = rows.get(pending.player());
        if (stored == null || !sameHop(stored, pending)) {
            return 0;
        }
        rows.remove(pending.player());
        return 1;
    }

    private static boolean sameHop(PendingHop a, PendingHop b) {
        return a.kind() == b.kind()
                && a.target().equals(b.target())
                && a.targetServer().equals(b.targetServer())
                && a.requestedAt().toEpochMilli() == b.requestedAt().toEpochMilli();
    }
}
