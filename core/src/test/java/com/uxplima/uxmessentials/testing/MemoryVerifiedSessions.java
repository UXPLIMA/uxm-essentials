package com.uxplima.uxmessentials.testing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;

/** The shared session record held in memory: hand one instance to every backend of a test to share it. */
public final class MemoryVerifiedSessions implements VerifiedSessions {

    private final Map<UUID, Row> rows = new ConcurrentHashMap<>();
    private volatile boolean proxySeen;

    @Override
    public Optional<Session> find(UUID player) {
        return Optional.ofNullable(rows.get(player)).map(Row::session);
    }

    @Override
    public synchronized void record(
            UUID player, String server, Optional<UUID> connection, Optional<String> ipHash, Instant now) {
        rows.put(player, new Row(new Session(server, connection, ipHash, Optional.empty()), now));
    }

    @Override
    public synchronized boolean take(UUID player, String server, Session seen, Instant now) {
        Row row = rows.get(player);
        if (row == null
                || !row.session().heldBy().equals(seen.heldBy())
                || !row.session().connection().equals(seen.connection())
                || !row.session().ipHash().equals(seen.ipHash())
                || row.session().expiresAt().map(end -> !now.isBefore(end)).orElse(false)) {
            return false;
        }
        Session held = row.session();
        rows.put(player, new Row(new Session(server, held.connection(), held.ipHash(), Optional.empty()), now));
        return true;
    }

    @Override
    public synchronized void release(UUID player, String server, Instant expiresAt) {
        Row row = rows.get(player);
        if (row != null
                && row.session().heldBy().equals(server)
                && row.session().expiresAt().isEmpty()) {
            rows.put(player, row.expiring(expiresAt));
        }
    }

    @Override
    public synchronized void releaseAll(String server, Instant startedAt) {
        rows.replaceAll((player, row) -> row.session().heldBy().equals(server)
                        && row.session().expiresAt().isEmpty()
                        && row.heldSince().isBefore(startedAt)
                ? row.expiring(startedAt)
                : row);
    }

    @Override
    public synchronized void revoke(UUID player) {
        rows.remove(player);
    }

    @Override
    public boolean proxySeen() {
        return proxySeen;
    }

    @Override
    public void markProxySeen(Instant at) {
        proxySeen = true;
    }

    @Override
    public void forgetProxySeen() {
        proxySeen = false;
    }

    private record Row(Session session, Instant heldSince) {

        Row expiring(Instant at) {
            Session held = session;
            return new Row(new Session(held.heldBy(), held.connection(), held.ipHash(), Optional.of(at)), heldSince);
        }
    }
}
