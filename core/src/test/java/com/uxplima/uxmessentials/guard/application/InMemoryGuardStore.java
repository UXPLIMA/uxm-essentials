package com.uxplima.uxmessentials.guard.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.guard.application.port.GuardStore;
import com.uxplima.uxmessentials.guard.application.port.GuardStoreException;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import org.jspecify.annotations.Nullable;

/** A guard store in memory, shared by the "servers" of a test the way a database would be. */
final class InMemoryGuardStore implements GuardStore {

    private final Map<RegionKey, Region> regions = new LinkedHashMap<>();
    private final List<HistoryEntry> history = new ArrayList<>();
    private final List<Denial> denials = new ArrayList<>();
    private long stamp;
    boolean failLoad;
    boolean failWrite;

    @Override
    public synchronized List<Region> load(Set<GuardScope> scopes) {
        if (failLoad) {
            throw new GuardStoreException("database is down", new IllegalStateException());
        }
        return regions.values().stream().filter(r -> scopes.contains(r.scope())).toList();
    }

    @Override
    public synchronized Optional<Region> find(GuardScope scope, RegionKey key) {
        return Optional.ofNullable(regions.get(key)).filter(r -> r.scope().equals(scope));
    }

    @Override
    public synchronized HistoryEntry write(
            @Nullable Region before, @Nullable Region after, String actor, String change) {
        if (failWrite) {
            throw new GuardStoreException("database is down", new IllegalStateException());
        }
        RegionKey key = after != null
                ? after.key()
                : Objects.requireNonNull(before, "before").key();
        if (after == null) {
            regions.remove(key);
        } else {
            regions.put(key, after);
        }
        stamp++;
        HistoryEntry entry = new HistoryEntry(
                history.size() + 1, Instant.EPOCH.plusSeconds(stamp), actor, key, change, before, after);
        history.add(entry);
        return entry;
    }

    @Override
    public synchronized List<HistoryEntry> history(@Nullable RegionKey key, int offset, int limit) {
        return history.stream()
                .filter(e -> key == null || e.key().equals(key))
                .sorted(Comparator.comparingLong(HistoryEntry::id).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized Optional<HistoryEntry> historyEntry(long id) {
        return history.stream().filter(e -> e.id() == id).findFirst();
    }

    @Override
    public synchronized long stamp(Set<GuardScope> scopes) {
        return stamp;
    }

    @Override
    public synchronized void logDenials(List<Denial> batch) {
        denials.addAll(batch);
    }

    @Override
    public synchronized List<Denial> denials(@Nullable String player, @Nullable String region, int offset, int limit) {
        return denials.stream().skip(offset).limit(limit).toList();
    }

    @Override
    public synchronized void pruneDenials(Instant before) {
        denials.removeIf(d -> d.at().isBefore(before));
    }

    /** Another server wrote straight to the shared database. */
    synchronized void writeFromElsewhere(Region region) {
        regions.put(region.key(), region);
        stamp++;
    }
}
