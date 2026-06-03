package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link PlayerWarpRepository} over the generated {@code PLAYER_WARPS} table. Player-warps are
 * owned per player and keyed by {@code (owner, name)}, so a lookup is a single-row {@code SELECT} on that
 * composite key, the owned and public lists read the owner's rows in stored creation order, the count is a
 * {@code COUNT(*)} so the limit check never materialises the set, and a {@code save} upserts on that key — a
 * re-anchor or a visibility flip overwrites the same row. Every statement is typed jOOQ DSL; no SQL is ever
 * string-concatenated.
 */
public final class JooqPlayerWarpRepository extends JooqRepository implements PlayerWarpRepository {

    private static final int PUBLIC = 1;

    public JooqPlayerWarpRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<PlayerWarp> find(PlayerRef owner, PlayerWarpName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.OWNER.eq(owner.uuid().toString()))
                .and(PLAYER_WARPS.NAME.eq(name.value()))
                .fetchOptional()
                .map(PlayerWarpRows::toPlayerWarp));
    }

    @Override
    public List<PlayerWarp> ownedBy(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.OWNER.eq(owner.uuid().toString()))
                .orderBy(PLAYER_WARPS.CREATED_AT.asc(), PLAYER_WARPS.NAME.asc())
                .fetch()
                .map(PlayerWarpRows::toPlayerWarp));
    }

    @Override
    public List<PlayerWarp> publicOf(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.OWNER.eq(owner.uuid().toString()))
                .and(PLAYER_WARPS.IS_PUBLIC.eq(PUBLIC))
                .orderBy(PLAYER_WARPS.CREATED_AT.asc(), PLAYER_WARPS.NAME.asc())
                .fetch()
                .map(PlayerWarpRows::toPlayerWarp));
    }

    @Override
    public int count(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl ->
                dsl.fetchCount(PLAYER_WARPS, PLAYER_WARPS.OWNER.eq(owner.uuid().toString())));
    }

    @Override
    public boolean exists(PlayerRef owner, PlayerWarpName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.fetchExists(
                PLAYER_WARPS, PLAYER_WARPS.OWNER.eq(owner.uuid().toString()).and(PLAYER_WARPS.NAME.eq(name.value()))));
    }

    @Override
    public void save(PlayerWarp warp) {
        Objects.requireNonNull(warp, "warp");
        write(dsl -> {
            upsert(dsl, warp);
            return null;
        });
    }

    @Override
    public void delete(PlayerRef owner, PlayerWarpName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        write(dsl -> dsl.deleteFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.OWNER.eq(owner.uuid().toString()))
                .and(PLAYER_WARPS.NAME.eq(name.value()))
                .execute());
    }

    private static void upsert(DSLContext dsl, PlayerWarp warp) {
        PlayerWarpsRecord record = dsl.newRecord(PLAYER_WARPS);
        PlayerWarpRows.apply(record, warp);
        dsl.insertInto(PLAYER_WARPS)
                .set(record)
                .onConflict(PLAYER_WARPS.OWNER, PLAYER_WARPS.NAME)
                .doUpdate()
                .set(PLAYER_WARPS.WORLD, record.getWorld())
                .set(PLAYER_WARPS.WORLD_NAME, record.getWorldName())
                .set(PLAYER_WARPS.X, record.getX())
                .set(PLAYER_WARPS.Y, record.getY())
                .set(PLAYER_WARPS.Z, record.getZ())
                .set(PLAYER_WARPS.YAW, record.getYaw())
                .set(PLAYER_WARPS.PITCH, record.getPitch())
                .set(PLAYER_WARPS.IS_PUBLIC, record.getIsPublic())
                .execute();
    }
}
