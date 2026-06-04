package com.uxplima.uxmessentials.persistence.vote;

import static com.uxplima.uxmessentials.persistence.jooq.tables.VoteParty.VOTE_PARTY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.VoteQueue.VOTE_QUEUE;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

/**
 * The jOOQ-backed {@link VoteRepository} over the generated {@code VOTE_PARTY} and {@code VOTE_QUEUE}
 * tables. The party counter is a single row at {@code id = 1}, so {@link #partyCount()} reads it
 * (defaulting to zero when no row exists yet), {@link #setPartyCount(int)} upserts it, and
 * {@link #incrementAndGetPartyCount()} adds one and returns the new value in a single atomic statement so
 * concurrent votes never lose an increment. The offline queue is one row per command keyed
 * {@code (player, idx)}: {@link #enqueue} appends a batch, computing each row's {@code idx} as
 * {@code MAX(idx)+1} inside the insert and retrying on a primary-key collision so two concurrent enqueues
 * for one player can never drop a vote, and {@link #drainFor} selects then deletes a player's rows in one
 * transaction so a batch pays out exactly once. Every statement is typed jOOQ DSL; no SQL is ever
 * string-concatenated.
 */
public final class JooqVoteRepository extends JooqRepository implements VoteRepository {

    private static final int PARTY_ROW_ID = 1;
    private static final int ENQUEUE_ATTEMPTS = 3;
    private static final String INTEGRITY_VIOLATION_SQL_STATE_CLASS = "23";

    public JooqVoteRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public int partyCount() {
        return read(dsl -> dsl.select(VOTE_PARTY.COUNT)
                .from(VOTE_PARTY)
                .where(VOTE_PARTY.ID.eq(PARTY_ROW_ID))
                .fetchOptional(VOTE_PARTY.COUNT)
                .orElse(0));
    }

    @Override
    public void setPartyCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        write(dsl -> dsl.insertInto(VOTE_PARTY)
                .set(VOTE_PARTY.ID, PARTY_ROW_ID)
                .set(VOTE_PARTY.COUNT, count)
                .onConflict(VOTE_PARTY.ID)
                .doUpdate()
                .set(VOTE_PARTY.COUNT, count)
                .execute());
    }

    @Override
    public int incrementAndGetPartyCount() {
        return write(dsl -> {
            Integer incremented = dsl.insertInto(VOTE_PARTY)
                    .set(VOTE_PARTY.ID, PARTY_ROW_ID)
                    .set(VOTE_PARTY.COUNT, 1)
                    .onConflict(VOTE_PARTY.ID)
                    .doUpdate()
                    .set(VOTE_PARTY.COUNT, VOTE_PARTY.COUNT.plus(1))
                    .returningResult(VOTE_PARTY.COUNT)
                    .fetchOne(VOTE_PARTY.COUNT);
            return Objects.requireNonNull(incremented, "incremented party count");
        });
    }

    @Override
    public void enqueue(QueuedReward reward) {
        Objects.requireNonNull(reward, "reward");
        // Each row's idx is derived from MAX(idx)+1 inside the same INSERT, so there is no read-then-insert
        // window of our own. Two genuinely concurrent enqueues can still both read the same MAX before either
        // commits and collide on the (player, idx) primary key; the bounded retry recomputes the next free
        // index for the loser rather than letting the collision escape as a dropped vote.
        for (int attempt = 1; attempt <= ENQUEUE_ATTEMPTS; attempt++) {
            try {
                write(dsl -> {
                    insertBatch(dsl, reward);
                    return null;
                });
                return;
            } catch (PersistenceException collision) {
                if (!isIntegrityViolation(collision) || attempt == ENQUEUE_ATTEMPTS) {
                    throw collision;
                }
            }
        }
    }

    @Override
    public List<QueuedReward> drainFor(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return write(dsl -> {
            QueuedReward batch = selectBatch(dsl, player);
            dsl.deleteFrom(VOTE_QUEUE)
                    .where(VOTE_QUEUE.PLAYER.eq(player.uuid().toString()))
                    .execute();
            return batch.commands().isEmpty() ? List.<QueuedReward>of() : List.of(batch);
        });
    }

    @Override
    public boolean hasPending(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return read(dsl ->
                dsl.fetchExists(VOTE_QUEUE, VOTE_QUEUE.PLAYER.eq(player.uuid().toString())));
    }

    private static void insertBatch(DSLContext dsl, QueuedReward reward) {
        String player = reward.player().uuid().toString();
        long queuedAt = reward.queuedAt().toEpochMilli();
        for (String command : reward.commands()) {
            // idx = COALESCE(MAX(idx), -1) + 1 over this player's rows, computed in the same statement that
            // inserts the row so a row added earlier in this batch is counted.
            dsl.insertInto(VOTE_QUEUE, VOTE_QUEUE.PLAYER, VOTE_QUEUE.IDX, VOTE_QUEUE.COMMAND, VOTE_QUEUE.QUEUED_AT)
                    .select(dsl.select(
                                    DSL.val(player),
                                    DSL.coalesce(DSL.max(VOTE_QUEUE.IDX), DSL.inline(-1))
                                            .plus(1),
                                    DSL.val(command),
                                    DSL.val(queuedAt))
                            .from(VOTE_QUEUE)
                            .where(VOTE_QUEUE.PLAYER.eq(player)))
                    .execute();
        }
    }

    /** True when {@code failure} (or a cause) is a SQL integrity-constraint violation (SQLState class 23). */
    private static boolean isIntegrityViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && state.startsWith(INTEGRITY_VIOLATION_SQL_STATE_CLASS)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static QueuedReward selectBatch(DSLContext dsl, PlayerRef player) {
        List<String> commands = new ArrayList<>();
        java.time.Instant queuedAt = java.time.Instant.EPOCH;
        for (Record row : dsl.selectFrom(VOTE_QUEUE)
                .where(VOTE_QUEUE.PLAYER.eq(player.uuid().toString()))
                .orderBy(VOTE_QUEUE.IDX.asc())
                .fetch()) {
            commands.add(VoteRows.toCommand(row));
            queuedAt = VoteRows.toQueuedAt(row);
        }
        return new QueuedReward(player, commands, queuedAt);
    }
}
