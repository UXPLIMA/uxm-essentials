package com.uxplima.uxmessentials.persistence.guard;

import static com.uxplima.uxmessentials.persistence.jooq.tables.GuardDenials.GUARD_DENIALS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.GuardHistory.GUARD_HISTORY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.GuardRegions.GUARD_REGIONS;

import com.uxplima.uxmessentials.guard.application.port.GuardStore;
import com.uxplima.uxmessentials.guard.application.port.GuardStoreException;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

/**
 * The guard store over jOOQ. A region write and its history row share one transaction. A region row that no
 * longer parses is skipped with a warning rather than failing the load, but a database that cannot be reached
 * fails the load, which locks the worlds.
 */
public final class JooqGuardStore extends JooqRepository implements GuardStore {

    private final Clock clock;
    private final java.util.function.Consumer<String> warn;

    public JooqGuardStore(DSLContext dsl, Clock clock, java.util.function.Consumer<String> warn) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    @Override
    public List<Region> load(Set<GuardScope> scopes) {
        try {
            return read(dsl -> {
                List<Region> out = new ArrayList<>();
                for (Record row : dsl.select(GUARD_REGIONS.SCOPE, GUARD_REGIONS.WORLD, GUARD_REGIONS.ID,
                                GUARD_REGIONS.DATA)
                        .from(GUARD_REGIONS)
                        .where(GUARD_REGIONS.SCOPE.in(scopes.stream().map(GuardScope::value).toList()))
                        .fetch()) {
                    try {
                        out.add(RegionJson.read(row.get(GUARD_REGIONS.DATA)));
                    } catch (RuntimeException broken) {
                        warn.accept("guard: region " + row.get(GUARD_REGIONS.WORLD) + ":" + row.get(GUARD_REGIONS.ID)
                                + " in " + row.get(GUARD_REGIONS.SCOPE) + " could not be read and is skipped: "
                                + broken.getMessage());
                    }
                }
                return out;
            });
        } catch (RuntimeException e) {
            throw new GuardStoreException("guard regions could not be loaded", e);
        }
    }

    @Override
    public Optional<Region> find(GuardScope scope, RegionKey key) {
        return read(dsl -> dsl.select(GUARD_REGIONS.DATA)
                .from(GUARD_REGIONS)
                .where(keyIs(scope, key))
                .fetchOptional(GUARD_REGIONS.DATA)
                .map(RegionJson::read));
    }

    @Override
    public HistoryEntry write(@Nullable Region before, @Nullable Region after, String actor, String change) {
        Region subject = after != null ? after : Objects.requireNonNull(before, "before");
        Instant now = clock.instant();
        try {
            return write(dsl -> writeIn(dsl, before, after, subject, actor, change, now));
        } catch (RuntimeException clash) {
            // Two servers can pick the same history id at once; the second attempt sees the first's row.
            return write(dsl -> writeIn(dsl, before, after, subject, actor, change, now));
        }
    }

    private HistoryEntry writeIn(
            DSLContext dsl, @Nullable Region before, @Nullable Region after, Region subject, String actor,
            String change, Instant now) {
        String who = actor.length() > 64 ? actor.substring(0, 64) : actor;
        if (before != null && (after == null || !before.scope().equals(after.scope()))) {
            dsl.deleteFrom(GUARD_REGIONS).where(keyIs(before.scope(), before.key())).execute();
        }
        if (after != null) {
            Long version = dsl.select(GUARD_REGIONS.VERSION)
                    .from(GUARD_REGIONS)
                    .where(keyIs(after.scope(), after.key()))
                    .fetchOne(GUARD_REGIONS.VERSION);
            String data = RegionJson.write(after);
            if (version == null) {
                dsl.insertInto(GUARD_REGIONS)
                        .set(GUARD_REGIONS.SCOPE, after.scope().value())
                        .set(GUARD_REGIONS.WORLD, after.world())
                        .set(GUARD_REGIONS.ID, after.id().value())
                        .set(GUARD_REGIONS.DATA, data)
                        .set(GUARD_REGIONS.IMPORT_BATCH, after.importBatch())
                        .set(GUARD_REGIONS.VERSION, 1L)
                        .set(GUARD_REGIONS.UPDATED_AT, now.toEpochMilli())
                        .set(GUARD_REGIONS.UPDATED_BY, who)
                        .execute();
            } else {
                dsl.update(GUARD_REGIONS)
                        .set(GUARD_REGIONS.DATA, data)
                        .set(GUARD_REGIONS.IMPORT_BATCH, after.importBatch())
                        .set(GUARD_REGIONS.VERSION, version + 1)
                        .set(GUARD_REGIONS.UPDATED_AT, now.toEpochMilli())
                        .set(GUARD_REGIONS.UPDATED_BY, who)
                        .where(keyIs(after.scope(), after.key()))
                        .execute();
            }
        }
        Long last = dsl.select(DSL.max(GUARD_HISTORY.ID)).from(GUARD_HISTORY).fetchOne(0, Long.class);
        long id = (last == null ? 0 : last) + 1;
        dsl.insertInto(GUARD_HISTORY)
                .set(GUARD_HISTORY.ID, id)
                .set(GUARD_HISTORY.HAPPENED_AT, now.toEpochMilli())
                .set(GUARD_HISTORY.ACTOR, who)
                .set(GUARD_HISTORY.SCOPE, subject.scope().value())
                .set(GUARD_HISTORY.WORLD, subject.world())
                .set(GUARD_HISTORY.REGION, subject.id().value())
                .set(GUARD_HISTORY.CHANGE_KIND, change)
                .set(GUARD_HISTORY.BEFORE_DATA, before == null ? null : RegionJson.write(before))
                .set(GUARD_HISTORY.AFTER_DATA, after == null ? null : RegionJson.write(after))
                .execute();
        return new HistoryEntry(id, now, who, subject.key(), change, before, after);
    }

    @Override
    public List<HistoryEntry> history(@Nullable RegionKey key, int offset, int limit) {
        return read(dsl -> {
            Condition where = key == null ? DSL.noCondition()
                    : GUARD_HISTORY.WORLD.eq(key.world()).and(GUARD_HISTORY.REGION.eq(key.id().value()));
            List<HistoryEntry> out = new ArrayList<>();
            for (Record row : dsl.selectFrom(GUARD_HISTORY)
                    .where(where)
                    .orderBy(GUARD_HISTORY.ID.desc())
                    .limit(Math.max(1, limit))
                    .offset(Math.max(0, offset))
                    .fetch()) {
                toEntry(row).ifPresent(out::add);
            }
            return out;
        });
    }

    @Override
    public Optional<HistoryEntry> historyEntry(long id) {
        return read(dsl -> dsl.selectFrom(GUARD_HISTORY)
                .where(GUARD_HISTORY.ID.eq(id))
                .fetchOptional()
                .flatMap(JooqGuardStore::toEntry));
    }

    @Override
    public long stamp(Set<GuardScope> scopes) {
        return read(dsl -> {
            Record row = dsl.select(DSL.max(GUARD_REGIONS.UPDATED_AT), DSL.count())
                    .from(GUARD_REGIONS)
                    .where(GUARD_REGIONS.SCOPE.in(scopes.stream().map(GuardScope::value).toList()))
                    .fetchOne();
            if (row == null) {
                return 0L;
            }
            Long max = row.get(0, Long.class);
            Integer count = row.get(1, Integer.class);
            return (max == null ? 0L : max) * 31 + (count == null ? 0 : count);
        });
    }

    @Override
    public void logDenials(List<Denial> denials) {
        if (denials.isEmpty()) {
            return;
        }
        write(dsl -> {
            for (Denial d : denials) {
                dsl.insertInto(GUARD_DENIALS)
                        .set(GUARD_DENIALS.HAPPENED_AT, d.at().toEpochMilli())
                        .set(GUARD_DENIALS.PLAYER, clip(d.player(), 16))
                        .set(GUARD_DENIALS.WORLD, clip(d.world(), 128))
                        .set(GUARD_DENIALS.X, d.x())
                        .set(GUARD_DENIALS.Y, d.y())
                        .set(GUARD_DENIALS.Z, d.z())
                        .set(GUARD_DENIALS.REGION, clip(d.region(), 64))
                        .set(GUARD_DENIALS.ACTION_ID, clip(d.action(), 96))
                        .execute();
            }
            return null;
        });
    }

    @Override
    public List<Denial> denials(@Nullable String player, @Nullable String region, int offset, int limit) {
        return read(dsl -> {
            Condition where = DSL.noCondition();
            if (player != null) {
                where = where.and(GUARD_DENIALS.PLAYER.equalIgnoreCase(player));
            }
            if (region != null) {
                where = where.and(GUARD_DENIALS.REGION.eq(region));
            }
            return dsl.selectFrom(GUARD_DENIALS)
                    .where(where)
                    .orderBy(GUARD_DENIALS.HAPPENED_AT.desc())
                    .limit(Math.max(1, limit))
                    .offset(Math.max(0, offset))
                    .fetch()
                    .map(r -> new Denial(
                            Instant.ofEpochMilli(r.get(GUARD_DENIALS.HAPPENED_AT)),
                            r.get(GUARD_DENIALS.PLAYER),
                            r.get(GUARD_DENIALS.WORLD),
                            r.get(GUARD_DENIALS.X),
                            r.get(GUARD_DENIALS.Y),
                            r.get(GUARD_DENIALS.Z),
                            r.get(GUARD_DENIALS.REGION),
                            r.get(GUARD_DENIALS.ACTION_ID)));
        });
    }

    @Override
    public void pruneDenials(Instant before) {
        write(dsl -> dsl.deleteFrom(GUARD_DENIALS)
                .where(GUARD_DENIALS.HAPPENED_AT.lt(before.toEpochMilli()))
                .execute());
    }

    private static Condition keyIs(GuardScope scope, RegionKey key) {
        return GUARD_REGIONS.SCOPE.eq(scope.value())
                .and(GUARD_REGIONS.WORLD.eq(key.world()))
                .and(GUARD_REGIONS.ID.eq(key.id().value()));
    }

    private static Optional<HistoryEntry> toEntry(Record row) {
        try {
            String before = row.get(GUARD_HISTORY.BEFORE_DATA);
            String after = row.get(GUARD_HISTORY.AFTER_DATA);
            return Optional.of(new HistoryEntry(
                    row.get(GUARD_HISTORY.ID),
                    Instant.ofEpochMilli(row.get(GUARD_HISTORY.HAPPENED_AT)),
                    row.get(GUARD_HISTORY.ACTOR),
                    new RegionKey(row.get(GUARD_HISTORY.WORLD), RegionId.of(row.get(GUARD_HISTORY.REGION))),
                    row.get(GUARD_HISTORY.CHANGE_KIND),
                    before == null ? null : RegionJson.read(before),
                    after == null ? null : RegionJson.read(after)));
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private static String clip(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
