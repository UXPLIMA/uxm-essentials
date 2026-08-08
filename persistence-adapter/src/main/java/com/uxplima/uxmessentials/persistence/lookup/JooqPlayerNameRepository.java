package com.uxplima.uxmessentials.persistence.lookup;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNames.PLAYER_NAMES;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link PlayerNameRepository} over the generated {@code PLAYER_NAMES} table. One row per account
 * holds the name it last joined under, its lower-cased form for matching, and the join timestamp. Every statement
 * is typed jOOQ DSL; no SQL is ever string-concatenated.
 *
 * <p>The upsert keys on the account, not on the name, so a rename replaces the account's row rather than adding a
 * second one, while two accounts that share a lower-cased name each keep their own row.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>DB-backed</b>. Every method borrows a pooled connection and must run off the tick thread. The
 * upsert serialises concurrent writes for one account at the database (SQLite single-writer WAL), so no JVM lock
 * is held here.
 */
@NullMarked
public final class JooqPlayerNameRepository extends JooqRepository implements PlayerNameRepository {

    public JooqPlayerNameRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public List<PlayerName> loadRecent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return read(dsl -> dsl.select(PLAYER_NAMES.UUID, PLAYER_NAMES.NAME, PLAYER_NAMES.LAST_SEEN)
                .from(PLAYER_NAMES)
                .orderBy(PLAYER_NAMES.LAST_SEEN.desc())
                .limit(limit)
                .fetch(row -> new PlayerName(UUID.fromString(row.value1()), row.value2(), row.value3())));
    }

    @Override
    public void upsert(PlayerName record) {
        Objects.requireNonNull(record, "record");
        String lower = record.name().toLowerCase(Locale.ROOT);
        write(dsl -> dsl.insertInto(PLAYER_NAMES)
                .set(PLAYER_NAMES.UUID, record.uuid().toString())
                .set(PLAYER_NAMES.NAME, record.name())
                .set(PLAYER_NAMES.LOWER_NAME, lower)
                .set(PLAYER_NAMES.LAST_SEEN, record.lastSeen())
                .onConflict(PLAYER_NAMES.UUID)
                .doUpdate()
                .set(PLAYER_NAMES.NAME, record.name())
                .set(PLAYER_NAMES.LOWER_NAME, lower)
                .set(PLAYER_NAMES.LAST_SEEN, record.lastSeen())
                .execute());
    }

    @Override
    public int count() {
        return read(dsl -> dsl.fetchCount(PLAYER_NAMES));
    }
}
