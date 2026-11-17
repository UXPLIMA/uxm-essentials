package com.uxplima.uxmessentials.persistence.security;

import static com.uxplima.uxmessentials.persistence.jooq.tables.SecuritySessions.SECURITY_SESSIONS;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The jOOQ {@link VerifiedSessions} over {@code security_sessions}, one row per account. A take and a release are
 * conditional updates, so a backend releasing a session another backend already took over changes nothing.
 */
@NullMarked
public final class JooqVerifiedSessions extends JooqRepository implements VerifiedSessions {

    public JooqVerifiedSessions(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Session> find(UUID player) {
        String id = Objects.requireNonNull(player, "player").toString();
        Record row = read(dsl -> dsl.select(
                        SECURITY_SESSIONS.HELD_BY,
                        SECURITY_SESSIONS.CONNECTION_ID,
                        SECURITY_SESSIONS.IP_HASH,
                        SECURITY_SESSIONS.EXPIRES_AT)
                .from(SECURITY_SESSIONS)
                .where(SECURITY_SESSIONS.UUID.eq(id))
                .fetchOne());
        return Optional.ofNullable(row).map(JooqVerifiedSessions::session);
    }

    @Override
    public void record(UUID player, String server, Optional<UUID> connection, Optional<String> ipHash) {
        String id = Objects.requireNonNull(player, "player").toString();
        Objects.requireNonNull(server, "server");
        @Nullable String connectionId = connection.map(UUID::toString).orElse(null);
        @Nullable String hash = ipHash.orElse(null);
        write(dsl -> dsl.insertInto(SECURITY_SESSIONS)
                .set(SECURITY_SESSIONS.UUID, id)
                .set(SECURITY_SESSIONS.HELD_BY, server)
                .set(SECURITY_SESSIONS.CONNECTION_ID, connectionId)
                .set(SECURITY_SESSIONS.IP_HASH, hash)
                .setNull(SECURITY_SESSIONS.EXPIRES_AT)
                .onConflict(SECURITY_SESSIONS.UUID)
                .doUpdate()
                .set(SECURITY_SESSIONS.HELD_BY, server)
                .set(SECURITY_SESSIONS.CONNECTION_ID, connectionId)
                .set(SECURITY_SESSIONS.IP_HASH, hash)
                .setNull(SECURITY_SESSIONS.EXPIRES_AT)
                .execute());
    }

    @Override
    public boolean take(UUID player, String server, Instant now) {
        String id = Objects.requireNonNull(player, "player").toString();
        Objects.requireNonNull(server, "server");
        long at = Objects.requireNonNull(now, "now").toEpochMilli();
        return write(dsl -> dsl.update(SECURITY_SESSIONS)
                        .set(SECURITY_SESSIONS.HELD_BY, server)
                        .setNull(SECURITY_SESSIONS.EXPIRES_AT)
                        .where(SECURITY_SESSIONS.UUID.eq(id))
                        .and(SECURITY_SESSIONS.EXPIRES_AT.isNull().or(SECURITY_SESSIONS.EXPIRES_AT.gt(at)))
                        .execute())
                > 0;
    }

    @Override
    public void release(UUID player, String server, Instant expiresAt) {
        String id = Objects.requireNonNull(player, "player").toString();
        Objects.requireNonNull(server, "server");
        long until = Objects.requireNonNull(expiresAt, "expiresAt").toEpochMilli();
        write(dsl -> dsl.update(SECURITY_SESSIONS)
                .set(SECURITY_SESSIONS.EXPIRES_AT, until)
                .where(SECURITY_SESSIONS.UUID.eq(id))
                .and(SECURITY_SESSIONS.HELD_BY.eq(server))
                .and(SECURITY_SESSIONS.EXPIRES_AT.isNull())
                .execute());
    }

    @Override
    public void releaseAll(String server, Instant at) {
        Objects.requireNonNull(server, "server");
        long until = Objects.requireNonNull(at, "at").toEpochMilli();
        write(dsl -> dsl.update(SECURITY_SESSIONS)
                .set(SECURITY_SESSIONS.EXPIRES_AT, until)
                .where(SECURITY_SESSIONS.HELD_BY.eq(server))
                .and(SECURITY_SESSIONS.EXPIRES_AT.isNull())
                .execute());
    }

    @Override
    public void revoke(UUID player) {
        String id = Objects.requireNonNull(player, "player").toString();
        write(dsl -> dsl.deleteFrom(SECURITY_SESSIONS)
                .where(SECURITY_SESSIONS.UUID.eq(id))
                .execute());
    }

    private static Session session(Record row) {
        @Nullable String connection = row.get(SECURITY_SESSIONS.CONNECTION_ID);
        @Nullable Long expiresAt = row.get(SECURITY_SESSIONS.EXPIRES_AT);
        return new Session(
                Objects.requireNonNull(row.get(SECURITY_SESSIONS.HELD_BY), "held_by"),
                Optional.ofNullable(connection).map(UUID::fromString),
                Optional.ofNullable(row.get(SECURITY_SESSIONS.IP_HASH)),
                Optional.ofNullable(expiresAt).map(Instant::ofEpochMilli));
    }
}
