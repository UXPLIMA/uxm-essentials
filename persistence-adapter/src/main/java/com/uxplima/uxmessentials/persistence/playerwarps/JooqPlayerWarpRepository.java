package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpBans.PLAYER_WARP_BANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpFavourites.PLAYER_WARP_FAVOURITES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpMembers.PLAYER_WARP_MEMBERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpPayments.PLAYER_WARP_PAYMENTS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpPendingTeleports.PLAYER_WARP_PENDING_TELEPORTS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatingRewards.PLAYER_WARP_RATING_REWARDS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatings.PLAYER_WARP_RATINGS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpVisits.PLAYER_WARP_VISITS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpWhitelist.PLAYER_WARP_WHITELIST;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link PlayerWarpRepository} over the V70 {@code player_warps} table. A warp is now keyed by a
 * durable surrogate {@link PlayerWarpId} and its name is globally unique, so {@link #findByName} and
 * {@link #existsByName} are single-column lookups, {@link #findById} keys on the surrogate, and the owner-scoped
 * lists read the owner's rows in stored creation order. A {@link #save} upserts on the surrogate — allocating a
 * fresh {@code max(id)+1} key on insert (the V5/V11/V69 idiom, so the schema needs no auto-increment) and updating
 * in place on an existing id, without ever touching the {@code password_*} columns. A {@link #deleteById} removes
 * the warp's side-table rows and the parent row in one transaction, since the schema carries no {@code ON DELETE
 * CASCADE}. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqPlayerWarpRepository extends JooqRepository implements PlayerWarpRepository {

    private final Function<UUID, String> names;
    private final LongSupplier randomSort;

    /** Backward-compatible: no profile resolver, so a null {@code owner_name} falls back to the uuid string. */
    public JooqPlayerWarpRepository(DSLContext dsl) {
        this(dsl, UUID::toString);
    }

    public JooqPlayerWarpRepository(DSLContext dsl, Function<UUID, String> names) {
        this(dsl, names, () -> ThreadLocalRandom.current().nextLong());
    }

    /**
     * @param randomSort the source of the {@code random_sort} ordering key stamped on each insert (and rewritten
     *     by {@link #reshuffle()}). A {@code RandomGenerator} in production; a test injects a deterministic
     *     supplier so a seeded RANDOM browse is reproducible. Never {@code Math.random()} on this hot write path.
     */
    public JooqPlayerWarpRepository(DSLContext dsl, Function<UUID, String> names, LongSupplier randomSort) {
        super(dsl);
        this.names = Objects.requireNonNull(names, "names");
        this.randomSort = Objects.requireNonNull(randomSort, "randomSort");
    }

    @Override
    public Optional<PlayerWarp> findByName(PlayerWarpName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.NAME.eq(name.value()))
                .fetchOptional()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public Optional<PlayerWarp> findById(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .fetchOptional()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public List<PlayerWarp> ownedBy(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.OWNER.eq(owner.uuid().toString()))
                .orderBy(PLAYER_WARPS.CREATED_AT.asc(), PLAYER_WARPS.ID.asc())
                .fetch()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.OWNER.eq(owner.uuid().toString()))
                .and(PLAYER_WARPS.STATUS.eq(WarpStatus.ACTIVE.name()))
                .and(PLAYER_WARPS.ACCESS.eq(WarpAccess.PUBLIC.name()))
                .orderBy(PLAYER_WARPS.CREATED_AT.asc(), PLAYER_WARPS.ID.asc())
                .fetch()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public List<PlayerWarp> all() {
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .orderBy(PLAYER_WARPS.OWNER.asc(), PLAYER_WARPS.CREATED_AT.asc(), PLAYER_WARPS.ID.asc())
                .fetch()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public int count(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl ->
                dsl.fetchCount(PLAYER_WARPS, PLAYER_WARPS.OWNER.eq(owner.uuid().toString())));
    }

    @Override
    public boolean existsByName(PlayerWarpName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.fetchExists(PLAYER_WARPS, PLAYER_WARPS.NAME.eq(name.value())));
    }

    @Override
    public PlayerWarpId save(PlayerWarp warp) {
        Objects.requireNonNull(warp, "warp");
        return write(dsl -> warp.id().map(id -> update(dsl, warp, id)).orElseGet(() -> insert(dsl, warp)));
    }

    @Override
    public void deleteById(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        long key = id.value();
        write(dsl -> {
            deleteSideRows(dsl, key);
            return dsl.deleteFrom(PLAYER_WARPS).where(PLAYER_WARPS.ID.eq(key)).execute();
        });
    }

    @Override
    public void recordVisit(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        // One guarded statement, not a read-modify-write, so two concurrent visits both land instead of racing.
        write(dsl -> dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.VISIT_COUNT, PLAYER_WARPS.VISIT_COUNT.add(1L))
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .execute());
    }

    /**
     * Assign every warp a fresh {@code random_sort} key so the RANDOM browse order is not frozen forever. One
     * transaction: read the ids, then batch an update per row binding a new random long. It is done application-side
     * because no single SQL {@code RANDOM()} name is portable across SQLite, MySQL, and PostgreSQL. Meant to be
     * called on a config cadence off the tick thread; wiring that scheduled task is a P6 follow-up. Returns how many
     * rows were reshuffled.
     */
    public int reshuffle() {
        return write(dsl -> {
            List<Long> ids = dsl.select(PLAYER_WARPS.ID).from(PLAYER_WARPS).fetch(PLAYER_WARPS.ID);
            List<Query> updates = new ArrayList<>(ids.size());
            for (Long id : ids) {
                updates.add(dsl.update(PLAYER_WARPS)
                        .set(PLAYER_WARPS.RANDOM_SORT, randomSort.getAsLong())
                        .where(PLAYER_WARPS.ID.eq(id)));
            }
            return updates.isEmpty() ? 0 : dsl.batch(updates).execute().length;
        });
    }

    private PlayerWarpId insert(DSLContext dsl, PlayerWarp warp) {
        long id = nextId(dsl);
        PlayerWarpsRecord record = dsl.newRecord(PLAYER_WARPS);
        PlayerWarpRows.apply(record, warp);
        record.setId(id);
        // random_sort is a persistence-only ordering key the RANDOM browse pages by; stamp it once here. The
        // update branch never touches it, so a visibility flip keeps a warp's place in the shuffle stable until a
        // reshuffle rewrites it.
        record.setRandomSort(randomSort.getAsLong());
        // A brand-new warp has no password, so the three password_* columns stay unset (NULL) on the insert.
        dsl.insertInto(PLAYER_WARPS).set(record).execute();
        return PlayerWarpId.of(id);
    }

    private PlayerWarpId update(DSLContext dsl, PlayerWarp warp, PlayerWarpId id) {
        PlayerWarpsRecord record = dsl.newRecord(PLAYER_WARPS);
        PlayerWarpRows.apply(record, warp);
        // apply() sets neither the id nor the password_* columns, so the UPDATE touches every other column but
        // leaves a set password intact — a visibility flip must never wipe it.
        dsl.update(PLAYER_WARPS)
                .set(record)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .execute();
        return id;
    }

    private static void deleteSideRows(DSLContext dsl, long warpId) {
        dsl.deleteFrom(PLAYER_WARP_RATINGS)
                .where(PLAYER_WARP_RATINGS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_VISITS)
                .where(PLAYER_WARP_VISITS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_BANS)
                .where(PLAYER_WARP_BANS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_WHITELIST)
                .where(PLAYER_WARP_WHITELIST.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_MEMBERS)
                .where(PLAYER_WARP_MEMBERS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_FAVOURITES)
                .where(PLAYER_WARP_FAVOURITES.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_PAYMENTS)
                .where(PLAYER_WARP_PAYMENTS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_RATING_REWARDS)
                .where(PLAYER_WARP_RATING_REWARDS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_PENDING_TELEPORTS)
                .where(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID.eq(warpId))
                .execute();
    }

    private static long nextId(DSLContext dsl) {
        Long maxId = dsl.select(DSL.max(PLAYER_WARPS.ID)).from(PLAYER_WARPS).fetchOne(0, Long.class);
        return (maxId == null ? 0L : maxId) + 1L;
    }
}
