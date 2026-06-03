package com.uxplima.uxmessentials.persistence.vote;

import static com.uxplima.uxmessentials.persistence.jooq.tables.VoteParty.VOTE_PARTY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.VoteQueue.VOTE_QUEUE;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * The jOOQ-backed {@link VoteRepository} over the generated {@code VOTE_PARTY} and {@code VOTE_QUEUE}
 * tables. The party counter is a single row at {@code id = 1}, so {@link #partyCount()} reads it
 * (defaulting to zero when no row exists yet) and {@link #setPartyCount(int)} upserts it. The offline
 * queue is one row per command keyed {@code (player, idx)}: {@link #enqueue} appends a batch's commands
 * with the next free indices, and {@link #drainFor} selects then deletes a player's rows in one
 * transaction so a batch pays out exactly once. Every statement is typed jOOQ DSL; no SQL is ever
 * string-concatenated.
 */
public final class JooqVoteRepository extends JooqRepository implements VoteRepository {

    private static final int PARTY_ROW_ID = 1;

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
    public void enqueue(QueuedReward reward) {
        Objects.requireNonNull(reward, "reward");
        write(dsl -> {
            int next = nextIndex(dsl, reward.player());
            insertBatch(dsl, reward, next);
            return null;
        });
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

    private static int nextIndex(DSLContext dsl, PlayerRef player) {
        Integer max = dsl.select(org.jooq.impl.DSL.max(VOTE_QUEUE.IDX))
                .from(VOTE_QUEUE)
                .where(VOTE_QUEUE.PLAYER.eq(player.uuid().toString()))
                .fetchOne(0, Integer.class);
        return max == null ? 0 : max + 1;
    }

    private static void insertBatch(DSLContext dsl, QueuedReward reward, int firstIndex) {
        String player = reward.player().uuid().toString();
        long queuedAt = reward.queuedAt().toEpochMilli();
        List<String> commands = reward.commands();
        for (int i = 0; i < commands.size(); i++) {
            dsl.insertInto(VOTE_QUEUE)
                    .set(VOTE_QUEUE.PLAYER, player)
                    .set(VOTE_QUEUE.IDX, firstIndex + i)
                    .set(VOTE_QUEUE.COMMAND, commands.get(i))
                    .set(VOTE_QUEUE.QUEUED_AT, queuedAt)
                    .execute();
        }
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
