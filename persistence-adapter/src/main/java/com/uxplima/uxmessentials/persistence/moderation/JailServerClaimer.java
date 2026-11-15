package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationJailLocations.MODERATION_JAIL_LOCATIONS;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the jail locations whose backend is not known yet: the rows the migration
 * marked {@value #UNSCOPED} because they predate the column, and the NULL rows a backend of the previous release writes
 * during a rolling upgrade. SQL cannot know which backend holds a jail's world, so this runs when the plugin starts,
 * once the worlds have loaded.
 *
 * <p>Only rows whose world uid is one this backend has loaded are taken, so the first backend to start never claims
 * the jails of another, and with no world loaded nothing is written at all. A row that already names a server is
 * never touched, which makes a second run claim nothing.
 */
@NullMarked
public final class JailServerClaimer extends JooqRepository {

    /** The server of a jail that existed before jails recorded one; never saved by a running backend. */
    public static final String UNSCOPED = StoredJail.UNSCOPED;

    public JailServerClaimer(DSLContext dsl) {
        super(dsl);
    }

    /** {@link #foreign} over the shared {@link Persistence} handle. */
    public static List<String> foreign(Persistence persistence, String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(persistence, "persistence");
        return new JailServerClaimer(persistence.dsl()).foreign(serverId, localWorldUids);
    }

    /** Claim over the shared {@link Persistence} handle, keeping jOOQ off the bukkit-adapter's classpath. */
    public static List<String> claim(Persistence persistence, String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(persistence, "persistence");
        return new JailServerClaimer(persistence.dsl()).claim(serverId, localWorldUids);
    }

    /**
     * Stamp {@code serverId} onto every jail of unknown server whose {@code world} uid is in {@code localWorldUids},
     * returning the names of the jails claimed, so the operator can see which ones this backend took.
     *
     * @param serverId this backend's non-blank network name, never {@value #UNSCOPED}
     * @param localWorldUids the uid strings of the worlds loaded on this backend; empty claims nothing
     */
    public List<String> claim(String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(localWorldUids, "localWorldUids");
        if (serverId.isBlank() || serverId.equals(UNSCOPED)) {
            throw new IllegalArgumentException("a jail cannot be claimed for the server '" + serverId + "'");
        }
        if (localWorldUids.isEmpty()) {
            return List.of();
        }
        Condition claimable = MODERATION_JAIL_LOCATIONS
                .SERVER_ID
                .isNull()
                .or(MODERATION_JAIL_LOCATIONS.SERVER_ID.eq(UNSCOPED))
                .and(MODERATION_JAIL_LOCATIONS.WORLD.in(localWorldUids));
        return write(dsl -> {
            List<String> names = dsl.select(MODERATION_JAIL_LOCATIONS.NAME)
                    .from(MODERATION_JAIL_LOCATIONS)
                    .where(claimable)
                    .orderBy(MODERATION_JAIL_LOCATIONS.NAME)
                    .fetch(MODERATION_JAIL_LOCATIONS.NAME);
            if (names.isEmpty()) {
                return List.<String>of();
            }
            // The update names its rows and repeats the condition, so a jail another backend claimed between the
            // read and the write is not taken back; the list is trimmed to the rows that were really written.
            dsl.update(MODERATION_JAIL_LOCATIONS)
                    .set(MODERATION_JAIL_LOCATIONS.SERVER_ID, serverId)
                    .where(MODERATION_JAIL_LOCATIONS.NAME.in(names))
                    .and(claimable)
                    .execute();
            return dsl.select(MODERATION_JAIL_LOCATIONS.NAME)
                    .from(MODERATION_JAIL_LOCATIONS)
                    .where(MODERATION_JAIL_LOCATIONS.NAME.in(names))
                    .and(MODERATION_JAIL_LOCATIONS.SERVER_ID.eq(serverId))
                    .orderBy(MODERATION_JAIL_LOCATIONS.NAME)
                    .fetch(MODERATION_JAIL_LOCATIONS.NAME);
        });
    }

    /**
     * The jails whose world is loaded here but which name another server: a server renamed since they were set, or a
     * copied world sharing its uid. Such a jail is sent to a server that may not exist, so the operator is told.
     */
    public List<String> foreign(String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(localWorldUids, "localWorldUids");
        if (localWorldUids.isEmpty()) {
            return List.of();
        }
        return read(dsl -> dsl.select(MODERATION_JAIL_LOCATIONS.NAME)
                .from(MODERATION_JAIL_LOCATIONS)
                .where(MODERATION_JAIL_LOCATIONS.WORLD.in(localWorldUids))
                .and(MODERATION_JAIL_LOCATIONS.SERVER_ID.isNotNull())
                .and(MODERATION_JAIL_LOCATIONS.SERVER_ID.ne(UNSCOPED))
                .and(MODERATION_JAIL_LOCATIONS.SERVER_ID.ne(serverId))
                .orderBy(MODERATION_JAIL_LOCATIONS.NAME)
                .fetch(MODERATION_JAIL_LOCATIONS.NAME));
    }
}
