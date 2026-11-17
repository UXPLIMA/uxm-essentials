package com.uxplima.uxmessentials.persistence.presence;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNicks.PLAYER_NICKS;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.presence.application.port.NickRecords;
import com.uxplima.uxmessentials.presence.domain.NickState;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link NickRecords} over {@code player_nicks}: one row per player who ever set a nick, an empty nick
 * after {@code /nick off}. A nick is written only over one set at the same time or earlier, so two backends writing at
 * once keep the newer one: an update guarded by {@code changed_at}, then an insert when no row exists, and the guarded
 * update again when another backend inserted first.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>DB-backed</b>. Every method borrows a pooled connection and must run off the tick thread.
 */
@NullMarked
public final class JooqNickRecords extends JooqRepository implements NickRecords {

    public JooqNickRecords(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public NickState read(UUID player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.selectFrom(PLAYER_NICKS)
                        .where(PLAYER_NICKS.PLAYER_UUID.eq(player.toString()))
                        .fetchOptional())
                .map(row -> new NickState(row.getNick(), row.getChangedAt()))
                .orElse(NickState.NONE);
    }

    @Override
    public void write(UUID player, NickState state) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(state, "state");
        String key = player.toString();
        if (write(dsl -> updateIfNotNewer(dsl, key, state)) > 0) {
            return;
        }
        if (read(dsl -> dsl.fetchExists(PLAYER_NICKS, PLAYER_NICKS.PLAYER_UUID.eq(key)))) {
            return; // the stored nick was set later than this one
        }
        try {
            write(dsl -> dsl.insertInto(PLAYER_NICKS)
                    .set(PLAYER_NICKS.PLAYER_UUID, key)
                    .set(PLAYER_NICKS.NICK, state.nick())
                    .set(PLAYER_NICKS.LOWER_NICK, lower(state.nick()))
                    .set(PLAYER_NICKS.CHANGED_AT, state.changedAtMillis())
                    .execute());
        } catch (PersistenceException raced) {
            // Another backend inserted the row between the existence check and this insert.
            write(dsl -> updateIfNotNewer(dsl, key, state));
        }
    }

    @Override
    public List<UUID> holders(String nick) {
        Objects.requireNonNull(nick, "nick");
        String wanted = lower(nick.strip());
        if (wanted.isEmpty()) {
            return List.of();
        }
        return read(dsl -> dsl.select(PLAYER_NICKS.PLAYER_UUID)
                .from(PLAYER_NICKS)
                .where(PLAYER_NICKS.LOWER_NICK.eq(wanted))
                .fetch(row -> UUID.fromString(row.value1())));
    }

    private static int updateIfNotNewer(DSLContext dsl, String player, NickState state) {
        return dsl.update(PLAYER_NICKS)
                .set(PLAYER_NICKS.NICK, state.nick())
                .set(PLAYER_NICKS.LOWER_NICK, lower(state.nick()))
                .set(PLAYER_NICKS.CHANGED_AT, state.changedAtMillis())
                .where(PLAYER_NICKS.PLAYER_UUID.eq(player))
                .and(PLAYER_NICKS.CHANGED_AT.le(state.changedAtMillis()))
                .execute();
    }

    private static String lower(String nick) {
        return nick.toLowerCase(Locale.ROOT);
    }
}
