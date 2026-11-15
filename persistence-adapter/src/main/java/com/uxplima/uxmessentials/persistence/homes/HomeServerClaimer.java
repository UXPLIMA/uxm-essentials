package com.uxplima.uxmessentials.persistence.homes;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Homes.HOMES;

import java.util.Collection;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the homes whose backend is not known yet: the rows the migration marked
 * {@value #UNSCOPED} because they predate the column, and the NULL rows a backend of the previous release or an
 * importer writes. SQL cannot know which backend holds a home's world, so this runs when the plugin starts, once the
 * worlds have loaded.
 *
 * <p>Only rows whose world uid is one this backend has loaded are taken, so the first backend to start never claims
 * the homes of another, and with no world loaded nothing is written at all. A row that already names a server is
 * never touched, which makes a second run claim nothing and keeps two backends starting at once from taking a row
 * back from each other.
 */
@NullMarked
public final class HomeServerClaimer extends JooqRepository {

    /**
     * The server of a home that existed before homes recorded one. Never saved by a running backend; a backend
     * configured under this name is refused, since its homes would read as unknown.
     */
    public static final String UNSCOPED = Home.UNSCOPED;

    public HomeServerClaimer(DSLContext dsl) {
        super(dsl);
    }

    /** Claim over the shared {@link Persistence} handle, keeping jOOQ off the bukkit-adapter's classpath. */
    public static int claim(Persistence persistence, String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(persistence, "persistence");
        return new HomeServerClaimer(persistence.dsl()).claim(serverId, localWorldUids);
    }

    /**
     * Stamp {@code serverId} onto every home of unknown server whose {@code world} uid is in {@code localWorldUids},
     * returning how many homes were claimed. A count rather than names, since a network can hold many thousands.
     *
     * @param serverId this backend's non-blank network name, never {@value #UNSCOPED}
     * @param localWorldUids the uid strings of the worlds loaded on this backend; empty claims nothing
     */
    public int claim(String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(localWorldUids, "localWorldUids");
        if (serverId.isBlank() || serverId.equals(UNSCOPED)) {
            throw new IllegalArgumentException("a home cannot be claimed for the server '" + serverId + "'");
        }
        if (localWorldUids.isEmpty()) {
            return 0;
        }
        Condition claimable =
                HOMES.SERVER_ID.isNull().or(HOMES.SERVER_ID.eq(UNSCOPED)).and(HOMES.WORLD.in(localWorldUids));
        return write(dsl -> dsl.update(HOMES)
                .set(HOMES.SERVER_ID, serverId)
                .where(claimable)
                .execute());
    }
}
