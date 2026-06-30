package com.uxplima.uxmessentials.persistence.menu;

import static com.uxplima.uxmessentials.persistence.jooq.tables.MenuPlayerData.MENU_PLAYER_DATA;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link PlayerDataRepository} over the generated {@code MENU_PLAYER_DATA} table. One row per
 * {@code (uuid, data_key)} holds that key's string value; {@link #upsert} is an insert-or-update on that composite
 * key, {@link #delete} removes the single row, and {@link #loadAll} fetches a player's whole key set in one scan
 * (the load-on-join cache warm). Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>DB-backed</b>. Every method borrows a pooled connection and must run off the tick thread; the
 * upsert serialises concurrent writes for one {@code (uuid, key)} at the database (SQLite single-writer WAL), so no
 * JVM lock is held here.
 */
@NullMarked
public final class JooqPlayerDataRepository extends JooqRepository implements PlayerDataRepository {

    public JooqPlayerDataRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Map<String, String> loadAll(UUID player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.select(MENU_PLAYER_DATA.DATA_KEY, MENU_PLAYER_DATA.DATA_VALUE)
                .from(MENU_PLAYER_DATA)
                .where(MENU_PLAYER_DATA.UUID.eq(player.toString()))
                .fetchMap(MENU_PLAYER_DATA.DATA_KEY, MENU_PLAYER_DATA.DATA_VALUE));
    }

    @Override
    public void upsert(UUID player, String key, String value) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        write(dsl -> dsl.insertInto(MENU_PLAYER_DATA)
                .set(MENU_PLAYER_DATA.UUID, player.toString())
                .set(MENU_PLAYER_DATA.DATA_KEY, key)
                .set(MENU_PLAYER_DATA.DATA_VALUE, value)
                .onConflict(MENU_PLAYER_DATA.UUID, MENU_PLAYER_DATA.DATA_KEY)
                .doUpdate()
                .set(MENU_PLAYER_DATA.DATA_VALUE, value)
                .execute());
    }

    @Override
    public void delete(UUID player, String key) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        write(dsl -> dsl.deleteFrom(MENU_PLAYER_DATA)
                .where(MENU_PLAYER_DATA.UUID.eq(player.toString()))
                .and(MENU_PLAYER_DATA.DATA_KEY.eq(key))
                .execute());
    }
}
