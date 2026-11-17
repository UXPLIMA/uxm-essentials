package com.uxplima.uxmessentials.persistence.lookup;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNames.PLAYER_NAMES;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.port.KnownAccounts;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link KnownAccounts} over the name index: an account joined a server of this database before when
 * the index holds a row for it, which every backend writes as a player joins.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>DB-backed</b>. Borrows a pooled connection and must run off the tick thread.
 */
@NullMarked
public final class JooqKnownAccounts extends JooqRepository implements KnownAccounts {

    public JooqKnownAccounts(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public boolean known(UUID player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.fetchExists(PLAYER_NAMES, PLAYER_NAMES.UUID.eq(player.toString())));
    }
}
