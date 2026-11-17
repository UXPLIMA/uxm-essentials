package com.uxplima.uxmessentials.persistence.messaging;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Ignores.IGNORES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNames.PLAYER_NAMES;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

/**
 * The jOOQ-backed {@link IgnoreStore} over the generated {@code IGNORES} table. The ignore list is
 * persistent (it survives restart, and ignore-aware {@code /msg} resolution reads it), keyed by
 * {@code (owner, ignored)} so a repeat {@code /ignore} upserts the same row rather than inserting a
 * duplicate. The stored scope is the {@link IgnoreScope} constant name; an unknown value read back is
 * treated as {@link IgnoreScope#ALL} so an ignore is never silently dropped. Every statement is typed jOOQ
 * DSL; no SQL is ever string-concatenated.
 *
 * <p>The ignored player's name is not persisted with the ignore (only the uuid is); it is read from the name
 * list every backend writes at each join, so the list shows the name the player last joined under. An account
 * that list has never seen keeps the uuid text as its name.
 */
public final class JooqIgnoreStore extends JooqRepository implements IgnoreStore {

    public JooqIgnoreStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public IgnoreList load(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<IgnoreEntry> entries = read(dsl -> dsl.select(IGNORES.IGNORED, IGNORES.SCOPE, PLAYER_NAMES.NAME)
                .from(IGNORES)
                .leftJoin(PLAYER_NAMES)
                .on(PLAYER_NAMES.UUID.eq(IGNORES.IGNORED))
                .where(IGNORES.OWNER.eq(owner.uuid().toString()))
                .fetch(row -> toEntry(row.value1(), row.value2(), row.value3())));
        return IgnoreList.of(owner, entries);
    }

    @Override
    public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ignored, "ignored");
        Objects.requireNonNull(scope, "scope");
        write(dsl -> upsert(dsl, owner, ignored, scope));
    }

    @Override
    public void unignore(PlayerRef owner, PlayerRef ignored) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ignored, "ignored");
        write(dsl -> dsl.deleteFrom(IGNORES)
                .where(IGNORES.OWNER.eq(owner.uuid().toString()))
                .and(IGNORES.IGNORED.eq(ignored.uuid().toString()))
                .execute());
    }

    private static int upsert(DSLContext dsl, PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
        return dsl.insertInto(IGNORES)
                .set(IGNORES.OWNER, owner.uuid().toString())
                .set(IGNORES.IGNORED, ignored.uuid().toString())
                .set(IGNORES.SCOPE, scope.name())
                .onConflict(IGNORES.OWNER, IGNORES.IGNORED)
                .doUpdate()
                .set(IGNORES.SCOPE, scope.name())
                .execute();
    }

    private static IgnoreEntry toEntry(String ignoredUuid, String scope, @Nullable String name) {
        UUID uuid = UUID.fromString(ignoredUuid);
        String shown = name == null || name.isBlank() ? ignoredUuid : name;
        return new IgnoreEntry(new PlayerRef(uuid, shown), IgnoreScope.fromStored(scope));
    }
}
