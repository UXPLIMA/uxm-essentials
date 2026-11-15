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

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the world rows no backend has claimed yet: the rows the migration marked
 * {@value NetworkWorld#UNSCOPED} because they predate the column. SQL cannot know which backend runs a world, so this
 * runs when the plugin starts, once the worlds have loaded.
 *
 * <p>A row is taken only for a world this backend actually has. When a world of the row's name is loaded here, the
 * row is taken only if it names that world's uid or no uid at all, so a backend never takes the row of a same-named
 * world of another backend. When none is loaded, a world folder of that name on this backend's disk is what counts.
 * A name this backend already keeps a row for is never taken, and a row that names a server is never touched, which
 * makes a second run claim nothing.
 */
@NullMarked
public final class WorldServerClaimer extends JooqRepository {

    public WorldServerClaimer(DSLContext dsl) {
        super(dsl);
    }

    /** {@link #claimPresent} over the shared {@link Persistence} handle, keeping jOOQ off the bukkit-adapter. */
    public static List<String> claimPresent(
            Persistence persistence, String serverId, Map<String, String> loadedUids, Collection<String> onDisk) {
        Objects.requireNonNull(persistence, "persistence");
        return new WorldServerClaimer(persistence.dsl()).claimPresent(serverId, loadedUids, onDisk);
    }

    /** {@link #claimNamed} over the shared {@link Persistence} handle. */
    public static boolean claimNamed(Persistence persistence, String serverId, String name) {
        Objects.requireNonNull(persistence, "persistence");
        return new WorldServerClaimer(persistence.dsl()).claimNamed(serverId, name);
    }

    /**
     * Stamp {@code serverId} onto every unclaimed world this backend has, with its settings, returning the names
     * claimed so the operator can see which ones this backend took.
     *
     * @param serverId this backend's non-blank network name, never {@value NetworkWorld#UNSCOPED}
     * @param loadedUids the uid of every world loaded here, by world name
     * @param onDisk the name of every world with a folder on this backend's disk, loaded or not
     */
    public List<String> claimPresent(String serverId, Map<String, String> loadedUids, Collection<String> onDisk) {
        requireClaimable(serverId);
        Objects.requireNonNull(loadedUids, "loadedUids");
        Objects.requireNonNull(onDisk, "onDisk");
        if (loadedUids.isEmpty() && onDisk.isEmpty()) {
            return List.of();
        }
        Set<String> folders = Set.copyOf(onDisk);
        return write(dsl -> {
            Set<String> kept = keptBy(dsl, serverId);
            List<String> claimable = new ArrayList<>();
            for (Record2<String, String> row : dsl.select(WORLD.NAME, WORLD.UID)
                    .from(WORLD)
                    .where(WORLD.SERVER_ID.eq(NetworkWorld.UNSCOPED))
                    .orderBy(WORLD.NAME)
                    .fetch()) {
                String name = row.value1();
                if (!kept.contains(name) && present(name, Optional.ofNullable(row.value2()), loadedUids, folders)) {
                    claimable.add(name);
                }
            }
            return move(dsl, serverId, claimable);
        });
    }

    /**
     * Stamp {@code serverId} onto the unclaimed world named {@code name}, for an operator who knows it belongs here.
     * False when no unclaimed world has that name, or this backend already keeps a world of that name.
     */
    public boolean claimNamed(String serverId, String name) {
        requireClaimable(serverId);
        Objects.requireNonNull(name, "name");
        return write(dsl -> !keptBy(dsl, serverId).contains(name)
                && !move(dsl, serverId, List.of(name)).isEmpty());
    }

    /** The rule {@link #claimPresent} applies to one unclaimed row; see the class comment. */
    static boolean present(String name, Optional<String> rowUid, Map<String, String> loadedUids, Set<String> folders) {
        String loadedUid = loadedUids.get(name);
        if (loadedUid != null) {
            return rowUid.map(loadedUid::equals).orElse(true);
        }
        return folders.contains(name);
    }

    /**
     * Move the unclaimed rows named {@code names} and their settings onto {@code serverId}, returning the names that
     * were really moved. The update repeats the unclaimed condition, so a row another backend claimed in between is
     * not taken back, and only the settings of the rows this call moved follow them.
     */
    private static List<String> move(DSLContext dsl, String serverId, List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        Set<String> before = keptBy(dsl, serverId);
        dsl.update(WORLD)
                .set(WORLD.SERVER_ID, serverId)
                .where(WORLD.SERVER_ID.eq(NetworkWorld.UNSCOPED))
                .and(WORLD.NAME.in(names))
                .execute();
        Set<String> after = keptBy(dsl, serverId);
        List<String> moved = names.stream()
                .filter(after::contains)
                .filter(name -> !before.contains(name))
                .toList();
        if (moved.isEmpty()) {
            return List.of();
        }
        // A setting row of this server with no world row would collide with the one moving in, so it goes first.
        dsl.deleteFrom(WORLD_SETTING)
                .where(WORLD_SETTING.SERVER_ID.eq(serverId))
                .and(WORLD_SETTING.WORLD_NAME.in(moved))
                .execute();
        dsl.update(WORLD_SETTING)
                .set(WORLD_SETTING.SERVER_ID, serverId)
                .where(WORLD_SETTING.SERVER_ID.eq(NetworkWorld.UNSCOPED))
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
