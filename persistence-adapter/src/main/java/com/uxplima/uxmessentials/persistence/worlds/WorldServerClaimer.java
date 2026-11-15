package com.uxplima.uxmessentials.persistence.worlds;

import static com.uxplima.uxmessentials.persistence.jooq.tables.World.WORLD;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WorldSetting.WORLD_SETTING;

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
 * Records this backend as the server of the world rows no networked backend has claimed yet: the rows the migration
 * marked {@value NetworkWorld#UNSCOPED} because they predate the column, and the rows a backend that recorded no
 * server (off a network, or under the shipped default name) kept there. SQL cannot know which backend runs a world, so
 * this runs when the plugin starts, once the worlds have loaded.
 *
 * <p>A row is taken only for a world this backend actually has. When a world of the row's name is loaded here under
 * the row's uid, or the row has no uid, the row moves here with its settings. When the loaded world has another uid,
 * the row belongs to another backend's copy of that name, so it stays unclaimed for that backend and this backend gets
 * a copy of it and its settings under its own uid: every backend that shared one row before servers were recorded
 * keeps the settings it had. When no world of that name is loaded, a world folder of that name on this backend's disk
 * counts. A name this backend already keeps is never touched, which makes a second run claim nothing.
 */
@NullMarked
public final class WorldServerClaimer extends JooqRepository {

    /**
     * What one start-up claim did.
     *
     * @param claimed the unclaimed rows moved onto this backend
     * @param copied the unclaimed rows of a same-named world of another uid, copied onto this backend
     */
    public record Claims(List<String> claimed, List<String> copied) {

        public Claims {
            claimed = List.copyOf(claimed);
            copied = List.copyOf(copied);
        }

        /** True when nothing was claimed or copied. */
        public boolean isEmpty() {
            return claimed.isEmpty() && copied.isEmpty();
        }
    }

    public WorldServerClaimer(DSLContext dsl) {
        super(dsl);
    }

    /** {@link #claimPresent} over the shared {@link Persistence} handle, keeping jOOQ off the bukkit-adapter. */
    public static Claims claimPresent(
            Persistence persistence, String serverId, Map<String, String> loadedUids, Collection<String> onDisk) {
        Objects.requireNonNull(persistence, "persistence");
        return new WorldServerClaimer(persistence.dsl()).claimPresent(serverId, loadedUids, onDisk);
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
     * Take onto {@code serverId} every unclaimed world this backend has, with its settings, as the class comment
     * describes, returning the names claimed and copied so the operator can see what this backend took.
     *
     * @param serverId this backend's non-blank network name, never {@value NetworkWorld#UNSCOPED}
     * @param loadedUids the uid of every world loaded here, by world name
     * @param onDisk the name of every world with a folder on this backend's disk, loaded or not
     */
    public Claims claimPresent(String serverId, Map<String, String> loadedUids, Collection<String> onDisk) {
        requireClaimable(serverId);
        Objects.requireNonNull(loadedUids, "loadedUids");
        Objects.requireNonNull(onDisk, "onDisk");
        if (loadedUids.isEmpty() && onDisk.isEmpty()) {
            return new Claims(List.of(), List.of());
        }
        Set<String> folders = Set.copyOf(onDisk);
        return write(dsl -> {
            Set<String> kept = keptBy(dsl, serverId);
            List<String> claimable = new ArrayList<>();
            List<String> copied = new ArrayList<>();
            for (Record2<String, String> row : dsl.select(WORLD.NAME, WORLD.UID)
                    .from(WORLD)
                    .where(WORLD.SERVER_ID.eq(NetworkWorld.UNSCOPED))
                    .orderBy(WORLD.NAME)
                    .fetch()) {
                String name = row.value1();
                if (kept.contains(name)) {
                    continue;
                }
                Optional<String> rowUid = Optional.ofNullable(row.value2());
                String loadedUid = loadedUids.get(name);
                if (loadedUid != null && rowUid.isPresent() && !rowUid.get().equals(loadedUid)) {
                    if (copy(dsl, serverId, name, loadedUid)) {
                        copied.add(name);
                    }
                } else if (loadedUid != null || folders.contains(name)) {
                    claimable.add(name);
                }
            }
            return new Claims(move(dsl, NetworkWorld.UNSCOPED, serverId, claimable), copied);
        });
    }

    /**
     * Stamp {@code serverId} onto the unclaimed world named {@code name}, for an operator who knows it belongs here.
     * False when no unclaimed world has that name, or this backend already keeps a world of that name.
     */
    public boolean claimNamed(String serverId, String name) {
        requireClaimable(serverId);
        Objects.requireNonNull(name, "name");
        return write(dsl ->
                !move(dsl, NetworkWorld.UNSCOPED, serverId, List.of(name)).isEmpty());
    }

    /**
     * Move every world recorded on {@code from} to {@code to}, with its settings, returning how many moved. A name
     * {@code to} already keeps stays on {@code from}. {@code to} may be {@value NetworkWorld#UNSCOPED}, which hands the
     * worlds back to the start-up claim; {@code from} may not, since those rows are the claim's to place.
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
            return move(dsl, from, to, names).size();
        });
    }

    /**
     * Copy the unclaimed row {@code name} and its settings onto {@code serverId} under this backend's {@code uid},
     * leaving the unclaimed row for the backend whose uid it records.
     */
    private static boolean copy(DSLContext dsl, String serverId, String name, String uid) {
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
        copy.setUid(uid);
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
