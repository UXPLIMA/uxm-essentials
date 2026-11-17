package com.uxplima.uxmessentials.testing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.security.application.port.AttemptStore;

/** The shared attempt record held in memory: hand one instance to every backend of a test to share it. */
public final class MemoryAttemptStore implements AttemptStore {

    private final Map<UUID, Integer> failures = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> locks = new ConcurrentHashMap<>();

    @Override
    public Optional<Instant> lockedUntil(UUID player) {
        return Optional.ofNullable(locks.get(player));
    }

    @Override
    public synchronized int addFailure(UUID player) {
        return failures.merge(player, 1, Integer::sum);
    }

    @Override
    public synchronized void lock(UUID player, Instant until) {
        locks.put(player, until);
        failures.put(player, 0);
    }

    @Override
    public synchronized void clearFailures(UUID player) {
        failures.computeIfPresent(player, (id, count) -> 0);
    }

    @Override
    public synchronized boolean clear(UUID player, Instant now) {
        failures.remove(player);
        Instant until = locks.remove(player);
        return until != null && now.isBefore(until);
    }
}
