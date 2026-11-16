package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationSanctionHistory.MODERATION_SANCTION_HISTORY;
import static com.uxplima.uxmessentials.persistence.runtime.ColumnFit.fit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToLongFunction;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.persistence.jooq.tables.ModerationSanctionHistory;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationSanctionHistoryRecord;
import com.uxplima.uxmessentials.persistence.runtime.IntegrityViolation;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record6;
import org.jooq.ResultQuery;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link SanctionHistory} over the generated V11 history table. {@link #append} is a single
 * insert with a synthetic {@code max(id)+1} key (the V5 warn-history pattern), tried again with a fresh key when
 * another writer took it first, so the table is only ever inserted into and read back: never updated. {@link
 * #countsSince} counts in SQL for {@code /modstats}. The family reads scope to a family by {@code action} and read
 * newest-first off the {@code (target, ts DESC)} index; the unified {@link #recentForTarget} read drops the
 * action filter so every kind folds into one timeline; {@link #recentByActor} scopes by {@code actor} (V28
 * index) for {@code /staffhistory}. Every row read caps at the supplied limit. The free-text columns are fitted to their
 * width before the insert: the sanction this row records is already stored, so a reason too long for the column must
 * not fail the history write on a backend that enforces the width. Every statement is typed jOOQ DSL
 * no SQL is ever string-concatenated, and writes go through the transactional {@code write} seam.
 */
@NullMarked
public final class JooqSanctionHistory extends JooqRepository implements SanctionHistory {

    /** How often a write whose key another writer took first is tried again with fresh keys. */
    static final int WRITE_ATTEMPTS = 3;

    private static final String ACTION_BAN = SanctionAction.BAN.name();
    private static final String ACTION_KICK = SanctionAction.KICK.name();

    private final ToLongFunction<DSLContext> firstFreeId;

    public JooqSanctionHistory(DSLContext dsl) {
        this(dsl, JooqSanctionHistory::maxIdPlusOne);
    }

    /** With {@code firstFreeId} standing in for the key read, so a test can hand out a key that is already taken. */
    JooqSanctionHistory(DSLContext dsl, ToLongFunction<DSLContext> firstFreeId) {
        super(dsl);
        this.firstFreeId = Objects.requireNonNull(firstFreeId, "firstFreeId");
    }

    @Override
    public void append(SanctionHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        appendAll(List.of(entry));
    }

    /**
     * One transaction for the whole batch: the keys are taken as one block after the current maximum, and the rows go
     * in as one JDBC batch, so a {@code /kickall} of hundreds of players is one write rather than one per player.
     */
    @Override
    public void appendAll(List<SanctionHistoryEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return;
        }
        // The keys are max(id)+1 onwards, read inside the transaction. Another server, or this one's async lane,
        // can read the same maximum and commit first; the loser hits the primary key and is tried again with keys
        // read afresh, so a sanction that is already stored never loses its history row to the race.
        for (int attempt = 1; ; attempt++) {
            try {
                write(dsl -> insert(dsl, entries));
                return;
            } catch (PersistenceException collision) {
                if (!IntegrityViolation.causedBy(collision) || attempt >= WRITE_ATTEMPTS) {
                    throw collision;
                }
            }
        }
    }

    private int insert(DSLContext dsl, List<SanctionHistoryEntry> entries) {
        long first = firstFreeId.applyAsLong(dsl);
        if (entries.size() == 1) {
            return dsl.insertInto(MODERATION_SANCTION_HISTORY)
                    .set(row(entries.get(0), first))
                    .execute();
        }
        List<ModerationSanctionHistoryRecord> rows = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            rows.add(row(entries.get(index), first + index));
        }
        return dsl.batchInsert(rows).execute().length;
    }

    @Override
    public List<SanctionHistoryEntry> banHistory(UUID target, int limit) {
        Objects.requireNonNull(target, "target");
        return scoped(target, limit, SanctionAction.BAN, SanctionAction.UNBAN);
    }

    @Override
    public List<SanctionHistoryEntry> muteHistory(UUID target, int limit) {
        Objects.requireNonNull(target, "target");
        return scoped(target, limit, SanctionAction.MUTE, SanctionAction.UNMUTE);
    }

    @Override
    public List<SanctionHistoryEntry> recentForTarget(UUID target, int limit) {
        Objects.requireNonNull(target, "target");
        return recentBy(MODERATION_SANCTION_HISTORY.TARGET, target, limit);
    }

    @Override
    public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
        Objects.requireNonNull(actor, "actor");
        return recentBy(MODERATION_SANCTION_HISTORY.ACTOR, actor, limit);
    }

    @Override
    public List<SanctionHistoryEntry> revocableByActor(UUID actor, int limit) {
        Objects.requireNonNull(actor, "actor");
        return read(dsl -> dsl.selectFrom(MODERATION_SANCTION_HISTORY)
                .where(MODERATION_SANCTION_HISTORY.ACTOR.eq(actor.toString()))
                .and(MODERATION_SANCTION_HISTORY.ACTION.in(
                        SanctionAction.BAN.name(), SanctionAction.MUTE.name(), SanctionAction.WARN.name()))
                .and(MODERATION_SANCTION_HISTORY.IP.isNull())
                .orderBy(MODERATION_SANCTION_HISTORY.TS.desc(), MODERATION_SANCTION_HISTORY.ID.desc())
                .limit(Math.max(0, limit))
                .fetch()
                .map(SanctionHistoryRows::toEntry));
    }

    @Override
    public List<Tally> countsSince(Instant threshold, Optional<UUID> actor) {
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(actor, "actor");
        return read(dsl -> countsQuery(dsl, threshold, actor)
                .fetch(record -> new Tally(
                        Optional.ofNullable(record.value1()).map(UUID::fromString),
                        record.value2(),
                        SanctionAction.valueOf(record.value3()),
                        ACTION_KICK.equals(record.value3()) ? record.value5() : record.value4(),
                        Instant.ofEpochMilli(Objects.requireNonNull(record.value6(), "latest")))));
    }

    /**
     * The {@link #countsSince} query: per actor and action, the rows, the distinct instants and the latest instant
     * since {@code threshold}. Separate so its SQL can be rendered for each dialect without a database.
     */
    static ResultQuery<Record6<String, String, String, Integer, Integer, Long>> countsQuery(
            DSLContext dsl, Instant threshold, Optional<UUID> actor) {
        ModerationSanctionHistory row = MODERATION_SANCTION_HISTORY.as("counted");
        ModerationSanctionHistory player = MODERATION_SANCTION_HISTORY.as("player_ban");
        // An IP row written at the same instant as the same staff member's ban of the same player is a STRICT ban's
        // collateral, not a ban of its own.
        Condition strictCollateral = DSL.exists(DSL.selectOne()
                .from(player)
                .where(player.ACTION.eq(ACTION_BAN))
                .and(player.IP.isNull())
                .and(player.TARGET.eq(row.TARGET))
                .and(player.TS.eq(row.TS))
                .and(player.ACTOR.isNotDistinctFrom(row.ACTOR)));
        Condition counted = row.ACTION
                .in(SanctionAction.MUTE.name(), SanctionAction.WARN.name(), ACTION_KICK)
                .or(row.ACTION.eq(ACTION_BAN).and(row.IP.isNull().or(DSL.not(strictCollateral))));
        Condition scope = actor.map(uuid -> row.ACTOR.eq(uuid.toString())).orElse(DSL.noCondition());
        Field<Integer> rows = DSL.count();
        // The kicks of one /kickall share their instant, so counting instants counts the command once.
        Field<Integer> instants = DSL.countDistinct(row.TS);
        Field<Long> latest = DSL.max(row.TS);
        return dsl.select(row.ACTOR, row.ACTOR_NAME, row.ACTION, rows, instants, latest)
                .from(row)
                .where(row.TS.ge(threshold.toEpochMilli()))
                .and(scope)
                .and(counted)
                .groupBy(row.ACTOR, row.ACTOR_NAME, row.ACTION);
    }

    private List<SanctionHistoryEntry> scoped(UUID target, int limit, SanctionAction a, SanctionAction b) {
        return read(dsl -> dsl.selectFrom(MODERATION_SANCTION_HISTORY)
                .where(MODERATION_SANCTION_HISTORY.TARGET.eq(target.toString()))
                .and(MODERATION_SANCTION_HISTORY.ACTION.in(a.name(), b.name()))
                .orderBy(MODERATION_SANCTION_HISTORY.TS.desc(), MODERATION_SANCTION_HISTORY.ID.desc())
                .limit(Math.max(0, limit))
                .fetch()
                .map(SanctionHistoryRows::toEntry));
    }

    private List<SanctionHistoryEntry> recentBy(
            TableField<ModerationSanctionHistoryRecord, String> column, UUID id, int limit) {
        return read(dsl -> dsl.selectFrom(MODERATION_SANCTION_HISTORY)
                .where(column.eq(id.toString()))
                .orderBy(MODERATION_SANCTION_HISTORY.TS.desc(), MODERATION_SANCTION_HISTORY.ID.desc())
                .limit(Math.max(0, limit))
                .fetch()
                .map(SanctionHistoryRows::toEntry));
    }

    /** The stored form of {@code entry} under key {@code id}, its free text fitted to the column widths. */
    private static ModerationSanctionHistoryRecord row(SanctionHistoryEntry entry, long id) {
        ModerationSanctionHistoryRecord row = new ModerationSanctionHistoryRecord();
        row.setId(id);
        row.setAction(entry.action().name());
        row.setTarget(entry.target().toString());
        row.setReason(entry.reason()
                .map(reason -> fit(MODERATION_SANCTION_HISTORY.REASON, reason))
                .orElse(null));
        row.setReasonKey(ReasonLines.key(entry.drawnReason()));
        row.setReasonArgs(ReasonLines.args(entry.drawnReason()));
        row.setActor(actorUuid(entry));
        row.setActorName(
                fit(MODERATION_SANCTION_HISTORY.ACTOR_NAME, entry.actor().name()));
        row.setIp(entry.ip().orElse(null));
        row.setExpiresAt(entry.expiry().map(Instant::toEpochMilli).orElse(null));
        row.setTs(entry.at().toEpochMilli());
        return row;
    }

    private static long maxIdPlusOne(DSLContext dsl) {
        Long maxId = dsl.select(DSL.max(MODERATION_SANCTION_HISTORY.ID))
                .from(MODERATION_SANCTION_HISTORY)
                .fetchOne(0, Long.class);
        return (maxId == null ? 0L : maxId) + 1L;
    }

    private static @org.jspecify.annotations.Nullable String actorUuid(SanctionHistoryEntry entry) {
        return entry.actor().uuid().map(UUID::toString).orElse(null);
    }
}
