package com.uxplima.uxmessentials.persistence.homes;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Homes.HOMES;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.HomesRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link HomeRepository} over the generated {@code HOMES} table. Reads rebuild an owner's
 * {@link HomeSet} from queryable rows in stored creation order; the quota count is a {@code COUNT(*)} so
 * the limit check never materialises the whole set. A {@code save} upserts on the {@code (owner, name)}
 * primary key — a re-anchor overwrites the same row — and a rename is an atomic keyed update inside one
 * transaction. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqHomeRepository extends JooqRepository implements HomeRepository {

    public JooqHomeRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public HomeSet load(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<Home> homes = read(dsl -> dsl.selectFrom(HOMES)
                .where(HOMES.OWNER.eq(owner.uuid().toString()))
                .orderBy(HOMES.CREATED_AT.asc(), HOMES.NAME.asc())
                .fetch()
                .map(row -> HomeRows.toHome(row, owner)));
        return HomeSet.of(owner, homes);
    }

    @Override
    public int count(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.fetchCount(HOMES, HOMES.OWNER.eq(owner.uuid().toString())));
    }

    @Override
    public void save(Home home) {
        Objects.requireNonNull(home, "home");
        write(dsl -> {
            upsert(dsl, home);
            return null;
        });
    }

    @Override
    public void rename(PlayerRef owner, HomeName from, HomeName to) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        write(dsl -> dsl.update(HOMES)
                .set(HOMES.NAME, to.value())
                .where(HOMES.OWNER.eq(owner.uuid().toString()))
                .and(HOMES.NAME.eq(from.value()))
                .execute());
    }

    @Override
    public void delete(PlayerRef owner, HomeName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        write(dsl -> dsl.deleteFrom(HOMES)
                .where(HOMES.OWNER.eq(owner.uuid().toString()))
                .and(HOMES.NAME.eq(name.value()))
                .execute());
    }

    private static void upsert(DSLContext dsl, Home home) {
        HomesRecord record = dsl.newRecord(HOMES);
        HomeRows.apply(record, home);
        dsl.insertInto(HOMES)
                .set(record)
                .onConflict(HOMES.OWNER, HOMES.NAME)
                .doUpdate()
                .set(HOMES.WORLD, record.getWorld())
                .set(HOMES.WORLD_NAME, record.getWorldName())
                .set(HOMES.X, record.getX())
                .set(HOMES.Y, record.getY())
                .set(HOMES.Z, record.getZ())
                .set(HOMES.YAW, record.getYaw())
                .set(HOMES.PITCH, record.getPitch())
                .execute();
    }
}
