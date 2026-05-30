package com.uxplima.uxmessentials.persistence.warps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Warps.WARPS;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.WarpsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link WarpRepository} over the generated {@code WARPS} table. Warps are server-wide and
 * keyed by name alone, so a lookup is a single-row {@code SELECT} on the {@code name} primary key, the list
 * reads every row in stored creation order, and a {@code save} upserts on that key — a re-anchor overwrites
 * the same row. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqWarpRepository extends JooqRepository implements WarpRepository {

    public JooqWarpRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Warp> find(WarpName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.selectFrom(WARPS)
                .where(WARPS.NAME.eq(name.value()))
                .fetchOptional()
                .map(WarpRows::toWarp));
    }

    @Override
    public List<Warp> all() {
        return read(dsl -> dsl.selectFrom(WARPS)
                .orderBy(WARPS.CREATED_AT.asc(), WARPS.NAME.asc())
                .fetch()
                .map(WarpRows::toWarp));
    }

    @Override
    public boolean exists(WarpName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.fetchExists(WARPS, WARPS.NAME.eq(name.value())));
    }

    @Override
    public void save(Warp warp) {
        Objects.requireNonNull(warp, "warp");
        write(dsl -> {
            upsert(dsl, warp);
            return null;
        });
    }

    @Override
    public void delete(WarpName name) {
        Objects.requireNonNull(name, "name");
        write(dsl -> dsl.deleteFrom(WARPS).where(WARPS.NAME.eq(name.value())).execute());
    }

    private static void upsert(DSLContext dsl, Warp warp) {
        WarpsRecord record = dsl.newRecord(WARPS);
        WarpRows.apply(record, warp);
        dsl.insertInto(WARPS)
                .set(record)
                .onConflict(WARPS.NAME)
                .doUpdate()
                .set(WARPS.WORLD, record.getWorld())
                .set(WARPS.WORLD_NAME, record.getWorldName())
                .set(WARPS.X, record.getX())
                .set(WARPS.Y, record.getY())
                .set(WARPS.Z, record.getZ())
                .set(WARPS.YAW, record.getYaw())
                .set(WARPS.PITCH, record.getPitch())
                .set(WARPS.OWNER, record.getOwner())
                .set(WARPS.COST, record.getCost())
                .set(WARPS.REQUIRED_PERMISSION, record.getRequiredPermission())
                .execute();
    }
}
