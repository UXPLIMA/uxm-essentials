package com.uxplima.uxmessentials.persistence.security;

import static com.uxplima.uxmessentials.persistence.jooq.tables.SecurityAttempts.SECURITY_ATTEMPTS;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.security.application.port.AttemptStore;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ {@link AttemptStore} over {@code security_attempts}, one row per account with wrong proofs or a lockout on
 * record. A failure is added by one upsert that adds to the count inside a transaction, and the count is read back
 * in the same transaction: the database serialises two backends adding at once on the row, so each
 * reads a count that includes the other's failure.
 */
@NullMarked
public final class JooqAttemptStore extends JooqRepository implements AttemptStore {

    public JooqAttemptStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Instant> lockedUntil(UUID player) {
        String id = Objects.requireNonNull(player, "player").toString();
        Long until = read(dsl -> dsl.select(SECURITY_ATTEMPTS.LOCKED_UNTIL)
                .from(SECURITY_ATTEMPTS)
                .where(SECURITY_ATTEMPTS.UUID.eq(id))
                .fetchOne(Record1::value1));
        return Optional.ofNullable(until).map(Instant::ofEpochMilli);
    }

    @Override
    public int addFailure(UUID player) {
        String id = Objects.requireNonNull(player, "player").toString();
        return writeRetrying(dsl -> {
            // One upsert rather than an insert and an update: two statements let two backends each hold half of the
            // row's locks and deadlock on MySQL.
            dsl.insertInto(SECURITY_ATTEMPTS)
                    .set(SECURITY_ATTEMPTS.UUID, id)
                    .set(SECURITY_ATTEMPTS.FAILURES, 1)
                    .onConflict(SECURITY_ATTEMPTS.UUID)
                    .doUpdate()
                    .set(SECURITY_ATTEMPTS.FAILURES, SECURITY_ATTEMPTS.FAILURES.plus(1))
                    .execute();
            Integer count = dsl.select(SECURITY_ATTEMPTS.FAILURES)
                    .from(SECURITY_ATTEMPTS)
                    .where(SECURITY_ATTEMPTS.UUID.eq(id))
                    .fetchOne(Record1::value1);
            return count == null ? 1 : count;
        });
    }

    @Override
    public void lock(UUID player, Instant until) {
        String id = Objects.requireNonNull(player, "player").toString();
        long end = Objects.requireNonNull(until, "until").toEpochMilli();
        write(dsl -> dsl.insertInto(SECURITY_ATTEMPTS)
                .set(SECURITY_ATTEMPTS.UUID, id)
                .set(SECURITY_ATTEMPTS.FAILURES, 0)
                .set(SECURITY_ATTEMPTS.LOCKED_UNTIL, end)
                .onConflict(SECURITY_ATTEMPTS.UUID)
                .doUpdate()
                .set(SECURITY_ATTEMPTS.FAILURES, 0)
                .set(SECURITY_ATTEMPTS.LOCKED_UNTIL, end)
                .execute());
    }

    @Override
    public void clearFailures(UUID player) {
        String id = Objects.requireNonNull(player, "player").toString();
        write(dsl -> dsl.update(SECURITY_ATTEMPTS)
                .set(SECURITY_ATTEMPTS.FAILURES, 0)
                .where(SECURITY_ATTEMPTS.UUID.eq(id))
                .execute());
    }

    @Override
    public void refundFailure(UUID player) {
        String id = Objects.requireNonNull(player, "player").toString();
        write(dsl -> dsl.update(SECURITY_ATTEMPTS)
                .set(SECURITY_ATTEMPTS.FAILURES, SECURITY_ATTEMPTS.FAILURES.minus(1))
                .where(SECURITY_ATTEMPTS.UUID.eq(id))
                .and(SECURITY_ATTEMPTS.FAILURES.gt(0))
                .execute());
    }

    @Override
    public void clearExpired(UUID player, Instant now) {
        String id = Objects.requireNonNull(player, "player").toString();
        long at = Objects.requireNonNull(now, "now").toEpochMilli();
        write(dsl -> dsl.deleteFrom(SECURITY_ATTEMPTS)
                .where(SECURITY_ATTEMPTS.UUID.eq(id))
                .and(SECURITY_ATTEMPTS.LOCKED_UNTIL.le(at))
                .execute());
    }

    @Override
    public boolean clear(UUID player, Instant now) {
        String id = Objects.requireNonNull(player, "player").toString();
        long at = Objects.requireNonNull(now, "now").toEpochMilli();
        return write(dsl -> {
            Long until = dsl.select(SECURITY_ATTEMPTS.LOCKED_UNTIL)
                    .from(SECURITY_ATTEMPTS)
                    .where(SECURITY_ATTEMPTS.UUID.eq(id))
                    .fetchOne(Record1::value1);
            dsl.deleteFrom(SECURITY_ATTEMPTS)
                    .where(SECURITY_ATTEMPTS.UUID.eq(id))
                    .execute();
            return until != null && until > at;
        });
    }
}
