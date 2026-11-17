package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationCaseCopies.MODERATION_CASE_COPIES;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.CaseCopies;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationCaseCopiesRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/** The {@link CaseCopies} kept in {@code moderation_case_copies}, which every server of the database shares. */
@NullMarked
public final class JooqCaseCopies extends JooqRepository implements CaseCopies {

    /** Accounts per query in {@link #heldBy}, within any database's IN list. */
    private static final int BATCH = 500;

    public JooqCaseCopies(DSLContext dsl) {
        super(dsl);
    }

    /** The record goes in first, so a copy that cannot be stored takes its record back out with it. */
    @Override
    public boolean copyMute(PlayerRef target, MuteState expected, MuteState copy, Instant at, Copy record) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(copy, "copy");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(record, "record");
        try {
            return write(dsl -> {
                ModerationWrites.ensureSeenRow(dsl, target, at);
                insert(dsl, record);
                if (!ModerationWrites.replaceMute(dsl, target, expected, copy)) {
                    throw new Superseded();
                }
                return true;
            });
        } catch (Superseded superseded) {
            return false;
        }
    }

    @Override
    public boolean copyJail(PlayerRef target, JailState.Active copy, Instant at, Copy record) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(copy, "copy");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(record, "record");
        return write(dsl -> {
            ModerationWrites.ensureSeenRow(dsl, target, at);
            insert(dsl, record);
            return ModerationWrites.saveJail(dsl, target, copy) == 1;
        });
    }

    private static void insert(DSLContext dsl, Copy record) {
        dsl.insertInto(MODERATION_CASE_COPIES)
                .set(MODERATION_CASE_COPIES.TARGET, record.target().toString())
                .set(MODERATION_CASE_COPIES.KIND, record.kind().name())
                .set(MODERATION_CASE_COPIES.ORIGIN, record.origin().toString())
                .set(MODERATION_CASE_COPIES.ISSUED_AT, record.issuedAt().toEpochMilli())
                .onConflictDoNothing()
                .execute();
    }

    /** Rolls the copy's transaction back when the mute it was to replace changed meanwhile. */
    private static final class Superseded extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Superseded() {
            super(null, null, false, false);
        }
    }

    @Override
    public List<Copy> heldBy(Collection<UUID> targets) {
        Objects.requireNonNull(targets, "targets");
        List<String> ids = targets.stream().map(UUID::toString).distinct().toList();
        List<Copy> copies = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<String> batch = ids.subList(from, Math.min(from + BATCH, ids.size()));
            read(dsl -> dsl.selectFrom(MODERATION_CASE_COPIES)
                            .where(MODERATION_CASE_COPIES.TARGET.in(batch))
                            .fetch())
                    .forEach(row -> copies.add(toCopy(row)));
        }
        return copies;
    }

    @Override
    public List<Copy> madeFrom(UUID origin, Kind kind) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(kind, "kind");
        return read(dsl -> dsl.selectFrom(MODERATION_CASE_COPIES)
                .where(MODERATION_CASE_COPIES.ORIGIN.eq(origin.toString()))
                .and(MODERATION_CASE_COPIES.KIND.eq(kind.name()))
                .fetch()
                .map(JooqCaseCopies::toCopy));
    }

    private static Copy toCopy(ModerationCaseCopiesRecord row) {
        return new Copy(
                UUID.fromString(row.getTarget()),
                Kind.valueOf(row.getKind()),
                UUID.fromString(row.getOrigin()),
                Instant.ofEpochMilli(row.getIssuedAt()));
    }
}
