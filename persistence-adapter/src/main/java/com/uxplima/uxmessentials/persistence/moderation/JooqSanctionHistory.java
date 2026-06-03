package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationSanctionHistory.MODERATION_SANCTION_HISTORY;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link SanctionHistory} over the generated V11 history table. {@link #append} is a single
 * insert with a synthetic {@code max(id)+1} key (the V5 warn-history pattern), so the table is only ever
 * inserted into and read back — never updated. The two read methods scope to a family by {@code action},
 * order newest-first off the {@code (target, ts DESC)} index, and cap at the supplied limit. Every statement
 * is typed jOOQ DSL — no SQL is ever string-concatenated — and writes go through the transactional
 * {@code write} seam.
 */
@NullMarked
public final class JooqSanctionHistory extends JooqRepository implements SanctionHistory {

    public JooqSanctionHistory(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void append(SanctionHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        write(dsl -> dsl.insertInto(MODERATION_SANCTION_HISTORY)
                .set(MODERATION_SANCTION_HISTORY.ID, nextId(dsl))
                .set(MODERATION_SANCTION_HISTORY.ACTION, entry.action().name())
                .set(MODERATION_SANCTION_HISTORY.TARGET, entry.target().toString())
                .set(MODERATION_SANCTION_HISTORY.REASON, entry.reason().orElse(null))
                .set(MODERATION_SANCTION_HISTORY.ACTOR, actorUuid(entry))
                .set(MODERATION_SANCTION_HISTORY.ACTOR_NAME, entry.actor().name())
                .set(MODERATION_SANCTION_HISTORY.IP, entry.ip().orElse(null))
                .set(
                        MODERATION_SANCTION_HISTORY.EXPIRES_AT,
                        entry.expiry().map(Instant::toEpochMilli).orElse(null))
                .set(MODERATION_SANCTION_HISTORY.TS, entry.at().toEpochMilli())
                .execute());
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

    private List<SanctionHistoryEntry> scoped(UUID target, int limit, SanctionAction a, SanctionAction b) {
        return read(dsl -> dsl.selectFrom(MODERATION_SANCTION_HISTORY)
                .where(MODERATION_SANCTION_HISTORY.TARGET.eq(target.toString()))
                .and(MODERATION_SANCTION_HISTORY.ACTION.in(a.name(), b.name()))
                .orderBy(MODERATION_SANCTION_HISTORY.TS.desc(), MODERATION_SANCTION_HISTORY.ID.desc())
                .limit(Math.max(0, limit))
                .fetch()
                .map(SanctionHistoryRows::toEntry));
    }

    private static long nextId(DSLContext dsl) {
        Long maxId = dsl.select(DSL.max(MODERATION_SANCTION_HISTORY.ID))
                .from(MODERATION_SANCTION_HISTORY)
                .fetchOne(0, Long.class);
        return (maxId == null ? 0L : maxId) + 1L;
    }

    private static @org.jspecify.annotations.Nullable String actorUuid(SanctionHistoryEntry entry) {
        return entry.actor().uuid().map(UUID::toString).orElse(null);
    }
}
