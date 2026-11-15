package com.uxplima.uxmessentials.persistence.warps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Warps.WARPS;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the warps whose backend is not known yet: the rows the migration marked
 * {@value #UNSCOPED} because they predate the column, and the NULL rows a backend of the previous release writes
 * during a rolling upgrade. SQL cannot know which backend holds a warp's world, so this runs when the plugin starts,
 * once the worlds have loaded.
 *
 * <p>Only rows whose world uid is one this backend has loaded are taken, so the first backend to start never claims
 * the warps of another, and with no world loaded nothing is written at all. A row that already names a server is
 * never touched, which makes a second run claim nothing.
 */
@NullMarked
public final class WarpServerClaimer extends JooqRepository {

    /**
     * The server of a warp that existed before warps recorded one. Never saved by a running backend; a backend
     * configured under this name is refused, since its warps would read as unknown.
     */
    public static final String UNSCOPED = com.uxplima.uxmessentials.warps.domain.Warp.UNSCOPED;

    public WarpServerClaimer(DSLContext dsl) {
        super(dsl);
    }

    /** Claim over the shared {@link Persistence} handle, keeping jOOQ off the bukkit-adapter's classpath. */
    public static List<String> claim(Persistence persistence, String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(persistence, "persistence");
        return new WarpServerClaimer(persistence.dsl()).claim(serverId, localWorldUids);
    }

    /**
     * Stamp {@code serverId} onto every warp of unknown server whose {@code world} uid is in {@code localWorldUids},
     * returning the names of the warps claimed, so the operator can see which ones this backend took.
     *
     * @param serverId this backend's non-blank network name, never {@value #UNSCOPED}
     * @param localWorldUids the uid strings of the worlds loaded on this backend; empty claims nothing
     */
    public List<String> claim(String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(localWorldUids, "localWorldUids");
        if (serverId.isBlank() || serverId.equals(UNSCOPED)) {
            throw new IllegalArgumentException("a warp cannot be claimed for the server '" + serverId + "'");
        }
        if (localWorldUids.isEmpty()) {
            return List.of();
        }
        Condition claimable =
                WARPS.SERVER_ID.isNull().or(WARPS.SERVER_ID.eq(UNSCOPED)).and(WARPS.WORLD.in(localWorldUids));
        return write(dsl -> {
            List<String> names = dsl.select(WARPS.NAME)
                    .from(WARPS)
                    .where(claimable)
                    .orderBy(WARPS.NAME)
                    .fetch(WARPS.NAME);
            if (names.isEmpty()) {
                return List.<String>of();
            }
            // The update names its rows and repeats the condition, so a warp another backend claimed between the
            // read and the write is not taken back; the list is trimmed to the rows that were really written.
            dsl.update(WARPS)
                    .set(WARPS.SERVER_ID, serverId)
                    .where(WARPS.NAME.in(names))
                    .and(claimable)
                    .execute();
            return dsl.select(WARPS.NAME)
                    .from(WARPS)
                    .where(WARPS.NAME.in(names))
                    .and(WARPS.SERVER_ID.eq(serverId))
                    .orderBy(WARPS.NAME)
                    .fetch(WARPS.NAME);
        });
    }
}
