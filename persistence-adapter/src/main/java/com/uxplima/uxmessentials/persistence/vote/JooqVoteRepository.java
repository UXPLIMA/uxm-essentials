package com.uxplima.uxmessentials.persistence.vote;

import static com.uxplima.uxmessentials.persistence.jooq.tables.VoteParty.VOTE_PARTY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.VotePartyParticipants.VOTE_PARTY_PARTICIPANTS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.VoteQueue.VOTE_QUEUE;
import static com.uxplima.uxmessentials.persistence.jooq.tables.VoteSiteCooldown.VOTE_SITE_COOLDOWN;
import static com.uxplima.uxmessentials.persistence.jooq.tables.VoteTotals.VOTE_TOTALS;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.VoteTotalsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.impl.DSL;

/**
 * The jOOQ-backed {@link VoteRepository} over the generated {@code VOTE_PARTY}, {@code VOTE_QUEUE}, and
 * {@code VOTE_TOTALS} tables. The party counter is a single row at {@code id = 1}, so {@link #partyCount()}
 * reads it (defaulting to zero when no row exists yet), {@link #setPartyCount(int)} upserts it, and
 * {@link #incrementAndGetPartyCount()} adds one and returns the new value in a single atomic statement so
 * concurrent votes never lose an increment. The offline queue is one row per command keyed
 * {@code (player, idx)}: {@link #enqueue} appends a batch, computing each row's {@code idx} as
 * {@code MAX(idx)+1} inside the insert and retrying on a primary-key collision so two concurrent enqueues
 * for one player can never drop a vote, and {@link #drainFor} selects then deletes a player's rows in one
 * transaction so a batch pays out exactly once. Vote totals are one row per player carrying all-time and
 * periodic (daily/weekly/monthly) counts plus period-window keys; {@link #saveTotals} upserts the row and
 * {@link #topVoters} orders by the period's column descending, excluding zero-count rows. Every statement
 * is typed jOOQ DSL; no SQL is ever string-concatenated.
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

    @Override
    public int queuedCount(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        // One row per queued command, so counting the player's rows yields the command count directly.
        return read(dsl -> {
            Integer count = dsl.selectCount()
                    .from(VOTE_QUEUE)
                    .where(VOTE_QUEUE.PLAYER.eq(player.uuid().toString()))
                    .fetchOne(0, Integer.class);
            return count == null ? 0 : count;
        });
    }

    @Override
    public VoteTally totalsOf(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.selectFrom(VOTE_TOTALS)
                .where(VOTE_TOTALS.PLAYER.eq(player.uuid().toString()))
                .fetchOptional()
                .map(JooqVoteRepository::toTally)
                .orElseGet(VoteTally::empty));
    }

    @Override
    public void saveTotals(PlayerRef player, VoteTally tally) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(tally, "tally");
        String uuid = player.uuid().toString();
        write(dsl -> dsl.insertInto(VOTE_TOTALS)
                .set(VOTE_TOTALS.PLAYER, uuid)
                .set(VOTE_TOTALS.ALLTIME, tally.alltime())
                .set(VOTE_TOTALS.DAILY, tally.daily())
                .set(VOTE_TOTALS.WEEKLY, tally.weekly())
                .set(VOTE_TOTALS.MONTHLY, tally.monthly())
                .set(VOTE_TOTALS.DAY_KEY, tally.dayKey())
                .set(VOTE_TOTALS.WEEK_KEY, tally.weekKey())
                .set(VOTE_TOTALS.MONTH_KEY, tally.monthKey())
                .set(VOTE_TOTALS.CURRENT_STREAK, tally.currentStreak())
                .set(VOTE_TOTALS.BEST_STREAK, tally.bestStreak())
                .set(VOTE_TOTALS.STREAK_DAY_KEY, tally.streakDayKey())
                .onConflict(VOTE_TOTALS.PLAYER)
                .doUpdate()
                .set(VOTE_TOTALS.ALLTIME, tally.alltime())
                .set(VOTE_TOTALS.DAILY, tally.daily())
                .set(VOTE_TOTALS.WEEKLY, tally.weekly())
                .set(VOTE_TOTALS.MONTHLY, tally.monthly())
                .set(VOTE_TOTALS.DAY_KEY, tally.dayKey())
                .set(VOTE_TOTALS.WEEK_KEY, tally.weekKey())
                .set(VOTE_TOTALS.MONTH_KEY, tally.monthKey())
                .set(VOTE_TOTALS.CURRENT_STREAK, tally.currentStreak())
                .set(VOTE_TOTALS.BEST_STREAK, tally.bestStreak())
                .set(VOTE_TOTALS.STREAK_DAY_KEY, tally.streakDayKey())
                .execute());
    }

    @Override
    public List<VoteRanking> topVoters(VotePeriod period, int limit) {
        Objects.requireNonNull(period, "period");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        TableField<VoteTotalsRecord, Long> col = periodColumn(period);
        return read(dsl -> dsl.select(VOTE_TOTALS.PLAYER, col)
                .from(VOTE_TOTALS)
                .where(col.gt(0L))
                .orderBy(col.desc())
                .limit(limit)
                .fetch(row -> {
                    String uuid = row.get(VOTE_TOTALS.PLAYER);
                    long votes = row.get(col);
                    // UUID stored as canonical 36-char text; name resolved by the adapter in V1-3.
                    PlayerRef ref = new PlayerRef(UUID.fromString(uuid), uuid);
                    return new VoteRanking(ref, votes);
                }));
    }

    // -------------------------------------------------------------------------
    // Party participants
    // -------------------------------------------------------------------------

    @Override
    public void markPartyParticipant(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        write(dsl -> dsl.insertInto(VOTE_PARTY_PARTICIPANTS)
                .set(VOTE_PARTY_PARTICIPANTS.PLAYER, player.uuid().toString())
                .onConflict(VOTE_PARTY_PARTICIPANTS.PLAYER)
                .doNothing()
                .execute());
    }

    @Override
    public Set<UUID> partyParticipants() {
        return read(dsl -> {
            Set<UUID> uuids = new HashSet<>();
            dsl.select(VOTE_PARTY_PARTICIPANTS.PLAYER)
                    .from(VOTE_PARTY_PARTICIPANTS)
                    .forEach(row -> uuids.add(UUID.fromString(row.get(VOTE_PARTY_PARTICIPANTS.PLAYER))));
            return uuids;
        });
    }

    @Override
    public void clearPartyParticipants() {
        write(dsl -> dsl.deleteFrom(VOTE_PARTY_PARTICIPANTS).execute());
    }

    // -------------------------------------------------------------------------
    // Party period key
    // -------------------------------------------------------------------------

    @Override
    public long partyPeriodKey() {
        return read(dsl -> dsl.select(VOTE_PARTY.PERIOD_KEY)
                .from(VOTE_PARTY)
                .where(VOTE_PARTY.ID.eq(PARTY_ROW_ID))
                .fetchOptional(VOTE_PARTY.PERIOD_KEY)
                .orElse(0L));
    }

    @Override
    public void setPartyPeriodKey(long key) {
        write(dsl -> dsl.insertInto(VOTE_PARTY)
                .set(VOTE_PARTY.ID, PARTY_ROW_ID)
                .set(VOTE_PARTY.COUNT, 0)
                .set(VOTE_PARTY.PERIOD_KEY, key)
                .onConflict(VOTE_PARTY.ID)
                .doUpdate()
                .set(VOTE_PARTY.PERIOD_KEY, key)
                .execute());
    }

    @Override
    public boolean claimPartyFire(int threshold) {
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least one: " + threshold);
        }
        // A single UPDATE that sets count=0 only when count>=threshold. SQLite serialises writes so
        // exactly one concurrent caller sees count>=threshold; the rest find count already at 0 and
        // update 0 rows, returning false.
        return write(dsl -> dsl.update(VOTE_PARTY)
                        .set(VOTE_PARTY.COUNT, 0)
                        .where(VOTE_PARTY.ID.eq(PARTY_ROW_ID).and(VOTE_PARTY.COUNT.ge(threshold)))
                        .execute()
                > 0);
    }

    // -------------------------------------------------------------------------
    // Threshold override
    // -------------------------------------------------------------------------

    @Override
    public int thresholdOverride() {
        return read(dsl -> dsl.select(VOTE_PARTY.THRESHOLD_OVERRIDE)
                .from(VOTE_PARTY)
                .where(VOTE_PARTY.ID.eq(PARTY_ROW_ID))
                .fetchOptional(VOTE_PARTY.THRESHOLD_OVERRIDE)
                .orElse(0));
    }

    @Override
    public void setThresholdOverride(int override) {
        if (override < 0) {
            throw new IllegalArgumentException("override must not be negative: " + override);
        }
        write(dsl -> dsl.insertInto(VOTE_PARTY)
                .set(VOTE_PARTY.ID, PARTY_ROW_ID)
                .set(VOTE_PARTY.COUNT, 0)
                .set(VOTE_PARTY.THRESHOLD_OVERRIDE, override)
                .onConflict(VOTE_PARTY.ID)
                .doUpdate()
                .set(VOTE_PARTY.THRESHOLD_OVERRIDE, override)
                .execute());
    }

    // -------------------------------------------------------------------------
    // Per-site cooldown tracking
    // -------------------------------------------------------------------------

    @Override
    public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(site, "site");
        String normalised = site.toLowerCase(java.util.Locale.ROOT);
        return read(dsl -> dsl.select(VOTE_SITE_COOLDOWN.LAST_VOTE_AT)
                .from(VOTE_SITE_COOLDOWN)
                .where(VOTE_SITE_COOLDOWN
                        .PLAYER
                        .eq(player.uuid().toString())
                        .and(VOTE_SITE_COOLDOWN.SITE_NAME.eq(normalised)))
                .fetchOptional(VOTE_SITE_COOLDOWN.LAST_VOTE_AT)
                .map(Instant::ofEpochMilli));
    }

    @Override
    public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(at, "at");
        String normalised = site.toLowerCase(java.util.Locale.ROOT);
        long epochMilli = at.toEpochMilli();
        write(dsl -> dsl.insertInto(VOTE_SITE_COOLDOWN)
                .set(VOTE_SITE_COOLDOWN.PLAYER, player.uuid().toString())
                .set(VOTE_SITE_COOLDOWN.SITE_NAME, normalised)
                .set(VOTE_SITE_COOLDOWN.LAST_VOTE_AT, epochMilli)
                .onConflict(VOTE_SITE_COOLDOWN.PLAYER, VOTE_SITE_COOLDOWN.SITE_NAME)
                .doUpdate()
                .set(VOTE_SITE_COOLDOWN.LAST_VOTE_AT, epochMilli)
                .execute());
    }

    // -------------------------------------------------------------------------
    // Admin reset
    // -------------------------------------------------------------------------

    @Override
    public void resetTotals(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        write(dsl -> dsl.deleteFrom(VOTE_TOTALS)
                .where(VOTE_TOTALS.PLAYER.eq(player.uuid().toString()))
                .execute());
    }

    // Maps a VotePeriod to its corresponding VOTE_TOTALS column.
    private static TableField<VoteTotalsRecord, Long> periodColumn(VotePeriod period) {
        return switch (period) {
            case DAILY -> VOTE_TOTALS.DAILY;
            case WEEKLY -> VOTE_TOTALS.WEEKLY;
            case MONTHLY -> VOTE_TOTALS.MONTHLY;
            case ALLTIME -> VOTE_TOTALS.ALLTIME;
        };
    }

    private static VoteTally toTally(VoteTotalsRecord row) {
        return new VoteTally(
                row.getAlltime(),
                row.getDaily(),
                row.getWeekly(),
                row.getMonthly(),
                row.getDayKey(),
                row.getWeekKey(),
                row.getMonthKey(),
                row.getCurrentStreak(),
                row.getBestStreak(),
                row.getStreakDayKey());
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
