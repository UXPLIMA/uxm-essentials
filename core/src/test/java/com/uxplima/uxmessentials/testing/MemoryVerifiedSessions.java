package com.uxplima.uxmessentials.testing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;

/** The shared session record held in memory: hand one instance to every backend of a test to share it. */
public final class MemoryVerifiedSessions implements VerifiedSessions {

    private final Map<UUID, Session> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<Session> find(UUID player) {
        return Optional.ofNullable(rows.get(player));
    }

    @Override
    public void record(UUID player, String server, Optional<UUID> connection, Optional<String> ipHash) {
        rows.put(player, new Session(server, connection, ipHash, Optional.empty()));
    }

    @Override
    public synchronized boolean take(UUID player, String server, Instant now) {
        Session row = rows.get(player);
        if (row == null || row.expiresAt().map(end -> !now.isBefore(end)).orElse(false)) {
            return false;
        }
        rows.put(player, new Session(server, row.connection(), row.ipHash(), Optional.empty()));
        return true;
    }

    @Override
    public synchronized void release(UUID player, String server, Instant expiresAt) {
        Session row = rows.get(player);
        if (row != null && row.heldBy().equals(server) && row.expiresAt().isEmpty()) {
            rows.put(player, new Session(server, row.connection(), row.ipHash(), Optional.of(expiresAt)));
        }
    }

    @Override
    public synchronized void releaseAll(String server, Instant at) {
        rows.replaceAll(
                (player, row) -> row.heldBy().equals(server) && row.expiresAt().isEmpty()
                        ? new Session(server, row.connection(), row.ipHash(), Optional.of(at))
                        : row);
    }

    @Override
    public void revoke(UUID player) {
        rows.remove(player);
    }
}
