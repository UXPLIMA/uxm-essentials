package com.uxplima.uxmessentials.persistence.worlds;

import static com.uxplima.uxmessentials.persistence.jooq.tables.World.WORLD;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WorldClaim.WORLD_CLAIM;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WorldSetting.WORLD_SETTING;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.WorldRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.WorldSettingRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jspecify.annotations.NullMarked;

/**
 * Gives this backend its own copy of the world rows no networked backend has claimed: the rows the migration marked
 * {@value NetworkWorld#UNSCOPED} because they predate the column, and the rows a backend that recorded no server (off a
 * network, or under the shipped default name) kept there. SQL cannot know which backend runs a world, so this runs
 * when the plugin starts, once the worlds have loaded.
 *
 * <p>A row is taken only for a world this backend actually has: one loaded here, or a world folder of that name on
 * this backend's disk. Each backend gets a copy of the row and its settings under its own uid, and the unclaimed row
 * stays as a template. A move would hand the row to whichever backend starts first: several backends may each hold a
 * copy of one world folder, uid and all, and before servers were recorded they all read that one row, so none of them
 * can tell from the uid that it is the only owner. A template some backend already copied is left out of the world
 * list. A name this backend already keeps is never touched.
 *
 * <p>The start-up claim runs once per server name: the first run records the name in {@code world_claim} in the same
 * transaction as the copies, and every later start skips it. So a world a backend unregisters is not copied back from
 * its template at the next start, and the template stays for a backend that has not started since the upgrade. A
 * world that arrives later is taken with {@link #claimNamed}. Templates are removed only by an operator: handing a
 * server's worlds back with {@link #rename} to {@value NetworkWorld#UNSCOPED} replaces them, and one nobody needs is
 * deleted by hand from {@code world} and {@code world_setting}.
 */
@NullMarked
public final class WorldServerClaimer extends JooqRepository {

    /**
     * What one start-up claim did.
     *
     * @param ran false when this server name had already run its start-up claim, so nothing was looked at
     * @param claimed the unclaimed rows this backend got its own copy of
     * @param conflicting the claimed rows whose template recorded another uid than the world this backend has, which
     *     means two different worlds shared one row before servers were recorded
     */
    public record Claims(boolean ran, List<String> claimed, List<String> conflicting) {

        public Claims {
            claimed = List.copyOf(claimed);
            conflicting = List.copyOf(conflicting);
        }

        static Claims alreadyRan() {
            return new Claims(false, List.of(), List.of());
        }

        /** True when nothing was claimed. */
        public boolean isEmpty() {
            return claimed.isEmpty();
        }
    }

    private final Clock clock;

    public WorldServerClaimer(DSLContext dsl) {
        this(dsl, Clock.systemUTC());
    }

    /** {@code clock} stamps the {@code world_claim} row. */
    public WorldServerClaimer(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** {@link #claimPresent} over the shared {@link Persistence} handle, keeping jOOQ off the bukkit-adapter. */
    public static Claims claimPresent(
            Persistence persistence, String serverId, Collection<String> present, Map<String, String> uids) {
        Objects.requireNonNull(persistence, "persistence");
        return new WorldServerClaimer(persistence.dsl()).claimPresent(serverId, present, uids);
    }

    /** {@link #claimNamed} over the shared {@link Persistence} handle. */
    public static boolean claimNamed(Persistence persistence, String serverId, String name) {
        Objects.requireNonNull(persistence, "persistence");
        return new WorldServerClaimer(persistence.dsl()).claimNamed(serverId, name);
    }

    /** {@link #rename} over the shared {@link Persistence} handle. */
    public static int rename(Persistence persistence, String from, String to) {
        Objects.requireNonNull(persistence, "persistence");
        return new WorldServerClaimer(persistence.dsl()).rename(from, to);
    }

    /**
     * Copy onto {@code serverId} every unclaimed world this backend has, with its settings, as the class comment
     * describes, returning the names claimed so the operator can see what this backend took. Does nothing when this
     * server name already ran it.
     *
     * @param serverId this backend's non-blank network name, never {@value NetworkWorld#UNSCOPED}
     * @param present the name of every world this backend has: loaded, or with a folder on its disk
     * @param uids the uid of each of those worlds that has one, from the loaded world or read from its folder
     */
    public Claims claimPresent(String serverId, Collection<String> present, Map<String, String> uids) {
        requireClaimable(serverId);
        Objects.requireNonNull(present, "present");
        Objects.requireNonNull(uids, "uids");
        Set<String> here = Set.copyOf(present);
        return write(dsl -> {
            if (hasRun(dsl, serverId)) {
                return Claims.alreadyRan();
            }
            markRun(dsl, serverId);
            Set<String> kept = keptBy(dsl, serverId);
            List<String> claimed = new ArrayList<>();
            List<String> conflicting = new ArrayList<>();
            for (Record2<String, String> row : dsl.select(WORLD.NAME, WORLD.UID)
                    .from(WORLD)
                    .where(WORLD.SERVER_ID.eq(NetworkWorld.UNSCOPED))
                    .orderBy(WORLD.NAME)
                    .fetch()) {
                String name = row.value1();
                if (kept.contains(name) || !here.contains(name)) {
                    continue;
                }
                Optional<String> uid = Optional.ofNullable(uids.get(name));
                if (copy(dsl, serverId, name, uid)) {
                    claimed.add(name);
                    Optional<String> rowUid = Optional.ofNullable(row.value2());
                    if (uid.isPresent() && rowUid.isPresent() && !rowUid.equals(uid)) {
                        conflicting.add(name);
                    }
                }
            }
            return new Claims(true, claimed, conflicting);
        });
    }

    /** Whether the server {@code serverId} has run its start-up claim. */
    public boolean hasRun(String serverId) {
        Objects.requireNonNull(serverId, "serverId");
        return read(dsl -> hasRun(dsl, serverId));
    }

    /**
     * Copy the unclaimed world named {@code name} onto {@code serverId} with its settings, for an operator who knows
     * it belongs here. False when no unclaimed world has that name, or this backend already keeps a world of that
     * name. The template stays, as it does for the start-up claim.
     */
    public boolean claimNamed(String serverId, String name) {
        requireClaimable(serverId);
        Objects.requireNonNull(name, "name");
        return write(dsl -> !keptBy(dsl, serverId).contains(name) && copy(dsl, serverId, name, Optional.empty()));
    }

    /**
     * Move every world recorded on {@code from} to {@code to}, with its settings, returning how many moved. A name
     * {@code to} already keeps stays on {@code from}. {@code to} may be {@value NetworkWorld#UNSCOPED}, which hands the
     * worlds back to the start-up claim and replaces a template of the same name, since the operator asked for this
     * server's current row to be the one the claim hands out; {@code from} may not, since those rows are the claim's
     * to place.
     */
    public int rename(String from, String to) {
        requireClaimable(from);
        Objects.requireNonNull(to, "to");
        if (to.isBlank()) {
            throw new IllegalArgumentException("worlds cannot be moved to a blank server");
        }
        if (from.equals(to)) {
            return 0;
        }
        return write(dsl -> {
            List<String> names = dsl.select(WORLD.NAME)
                    .from(WORLD)
                    .where(WORLD.SERVER_ID.eq(from))
                    .orderBy(WORLD.NAME)
                    .fetch(WORLD.NAME);
            if (to.equals(NetworkWorld.UNSCOPED) && !names.isEmpty()) {
                dropTemplates(dsl, names);
            }
            // The worlds of the old name were claimed already; a claim under the new name would copy back the
            // templates of worlds that server has since unregistered.
            if (!to.equals(NetworkWorld.UNSCOPED) && hasRun(dsl, from) && !hasRun(dsl, to)) {
                markRun(dsl, to);
            }
            return move(dsl, from, to, names).size();
        });
    }

    private static boolean hasRun(DSLContext dsl, String serverId) {
        return dsl.fetchExists(WORLD_CLAIM, WORLD_CLAIM.SERVER_ID.eq(serverId));
    }

    private void markRun(DSLContext dsl, String serverId) {
        dsl.insertInto(WORLD_CLAIM)
                .set(WORLD_CLAIM.SERVER_ID, serverId)
                .set(WORLD_CLAIM.CLAIMED_AT, clock.millis())
                .execute();
    }

    /** Drop the unclaimed rows named {@code names} and their settings before a server's own rows replace them. */
    private static void dropTemplates(DSLContext dsl, Collection<String> names) {
        dsl.deleteFrom(WORLD_SETTING)
                .where(WORLD_SETTING.SERVER_ID.eq(NetworkWorld.UNSCOPED))
                .and(WORLD_SETTING.WORLD_NAME.in(names))
                .execute();
        dsl.deleteFrom(WORLD)
                .where(WORLD.SERVER_ID.eq(NetworkWorld.UNSCOPED))
                .and(WORLD.NAME.in(names))
                .execute();
    }

    /**
     * Copy the unclaimed row {@code name} and its settings onto {@code serverId}, recording {@code uid} as the uid of
     * this backend's world. Without one the copy records none rather than the template's, which may name another
     * backend's world: the uid is filled in once the world loads here.
     */
    private static boolean copy(DSLContext dsl, String serverId, String name, Optional<String> uid) {
        Optional<WorldRecord> source = dsl.selectFrom(WORLD)
                .where(WORLD.SERVER_ID.eq(NetworkWorld.UNSCOPED))
                .and(WORLD.NAME.eq(name))
                .fetchOptional();
        if (source.isEmpty()) {
            return false;
        }
        WorldRecord copy = source.get().copy();
        copy.setServerId(serverId);
        copy.setName(name);
        copy.setUid(uid.orElse(null));
        dsl.insertInto(WORLD).set(copy).execute();
        // A setting row of this server with no world row would collide with the copies, so it goes first.
        dsl.deleteFrom(WORLD_SETTING)
                .where(WORLD_SETTING.SERVER_ID.eq(serverId))
                .and(WORLD_SETTING.WORLD_NAME.eq(name))
                .execute();
        for (WorldSettingRecord setting : dsl.selectFrom(WORLD_SETTING)
                .where(WORLD_SETTING.SERVER_ID.eq(NetworkWorld.UNSCOPED))
                .and(WORLD_SETTING.WORLD_NAME.eq(name))
                .fetch()) {
            dsl.insertInto(WORLD_SETTING)
                    .set(WORLD_SETTING.SERVER_ID, serverId)
                    .set(WORLD_SETTING.WORLD_NAME, name)
                    .set(WORLD_SETTING.SETTING_KEY, setting.getSettingKey())
                    .set(WORLD_SETTING.SETTING_VALUE, setting.getSettingValue())
                    .execute();
        }
        return true;
    }

    /**
     * Move the rows named {@code names} from {@code from} to {@code to} with their settings, returning the names that
     * were really moved. A name {@code to} already keeps is skipped, and the update repeats the source condition, so a
     * row another backend took in between is not taken back; only the settings of the rows this call moved follow.
     */
    private static List<String> move(DSLContext dsl, String from, String to, List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        Set<String> before = keptBy(dsl, to);
        List<String> wanted =
                names.stream().filter(name -> !before.contains(name)).toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        dsl.update(WORLD)
                .set(WORLD.SERVER_ID, to)
                .where(WORLD.SERVER_ID.eq(from))
                .and(WORLD.NAME.in(wanted))
                .execute();
        Set<String> after = keptBy(dsl, to);
        List<String> moved = wanted.stream().filter(after::contains).toList();
        if (moved.isEmpty()) {
            return List.of();
        }
        // A setting row of the target with no world row would collide with the one moving in, so it goes first.
        dsl.deleteFrom(WORLD_SETTING)
                .where(WORLD_SETTING.SERVER_ID.eq(to))
                .and(WORLD_SETTING.WORLD_NAME.in(moved))
                .execute();
        dsl.update(WORLD_SETTING)
                .set(WORLD_SETTING.SERVER_ID, to)
                .where(WORLD_SETTING.SERVER_ID.eq(from))
                .and(WORLD_SETTING.WORLD_NAME.in(moved))
                .execute();
        return moved;
    }

    private static Set<String> keptBy(DSLContext dsl, String serverId) {
        return new HashSet<>(dsl.select(WORLD.NAME)
                .from(WORLD)
                .where(WORLD.SERVER_ID.eq(serverId))
                .fetch(WORLD.NAME));
    }

    private static void requireClaimable(String serverId) {
        Objects.requireNonNull(serverId, "serverId");
        if (serverId.isBlank() || serverId.equals(NetworkWorld.UNSCOPED)) {
            throw new IllegalArgumentException("a world cannot be claimed for the server '" + serverId + "'");
        }
    }
}
