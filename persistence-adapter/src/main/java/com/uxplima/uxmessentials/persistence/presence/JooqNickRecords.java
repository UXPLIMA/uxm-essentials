package com.uxplima.uxmessentials.persistence.presence;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNicks.PLAYER_NICKS;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.presence.application.port.NickRecords;
import com.uxplima.uxmessentials.presence.application.port.NickWrite;
import com.uxplima.uxmessentials.presence.domain.NickState;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The jOOQ-backed {@link NickRecords} over {@code player_nicks}: one row per player who ever set a nick, an empty nick
 * after {@code /nick off}. The unique index on the lower-cased nick keeps a nick to one player, and every write names
 * the version it read: an update counts the version up only from the one named, and the first row of a player is an
 * insert. A write the index or a concurrent writer refused is read back to tell which of the two it was, the same way
 * on every dialect.
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
        return read(dsl -> dsl.select(PLAYER_NICKS.NICK, PLAYER_NICKS.VERSION)
                        .from(PLAYER_NICKS)
                        .where(PLAYER_NICKS.PLAYER_UUID.eq(player.toString()))
                        .fetchOptional())
                .map(row -> new NickState(row.value1(), row.value2()))
                .orElse(NickState.NONE);
    }

    @Override
    public NickWrite write(UUID player, long version, String nick) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(nick, "nick");
        String key = player.toString();
        @Nullable String lower = nick.isEmpty() ? null : lower(nick);
        long next = version + 1;
        try {
            if (version == 0L) {
                write(dsl -> dsl.insertInto(PLAYER_NICKS)
                        .set(PLAYER_NICKS.PLAYER_UUID, key)
                        .set(PLAYER_NICKS.NICK, nick)
                        .set(PLAYER_NICKS.LOWER_NICK, lower)
                        .set(PLAYER_NICKS.VERSION, next)
                        .execute());
                return new NickWrite.Written(new NickState(nick, next));
            }
            int updated = write(dsl -> dsl.update(PLAYER_NICKS)
                    .set(PLAYER_NICKS.NICK, nick)
                    .set(PLAYER_NICKS.LOWER_NICK, lower)
                    .set(PLAYER_NICKS.VERSION, next)
                    .where(PLAYER_NICKS.PLAYER_UUID.eq(key))
                    .and(PLAYER_NICKS.VERSION.eq(version))
                    .execute());
            return updated > 0 ? new NickWrite.Written(new NickState(nick, next)) : new NickWrite.Stale();
        } catch (PersistenceException refused) {
            return refusal(player, version, lower, refused);
        }
    }

    /**
     * Why a write was refused: another player holds the nick, or the row is no longer at the version written over (a
     * first row another backend inserted meanwhile). Anything else is a failure of the store and is thrown on.
     */
    private NickWrite refusal(UUID player, long version, @Nullable String lower, PersistenceException refused) {
        if (lower != null) {
            for (UUID holder : holdersOfLower(lower)) {
                if (!holder.equals(player)) {
                    return new NickWrite.Taken();
                }
            }
        }
        if (read(player).version() != version) {
            return new NickWrite.Stale();
        }
        throw refused;
    }

    @Override
    public List<UUID> holders(String nick) {
        Objects.requireNonNull(nick, "nick");
        String wanted = lower(nick.strip());
        return wanted.isEmpty() ? List.of() : holdersOfLower(wanted);
    }

    private List<UUID> holdersOfLower(String lower) {
        return read(dsl -> dsl.select(PLAYER_NICKS.PLAYER_UUID)
                .from(PLAYER_NICKS)
                .where(PLAYER_NICKS.LOWER_NICK.eq(lower))
                .fetch(row -> UUID.fromString(row.value1())));
    }

    private static String lower(String nick) {
        return nick.toLowerCase(Locale.ROOT);
    }
}
