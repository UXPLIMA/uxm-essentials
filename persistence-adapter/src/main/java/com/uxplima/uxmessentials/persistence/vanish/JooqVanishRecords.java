package com.uxplima.uxmessentials.persistence.vanish;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerVanish.PLAYER_VANISH;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.vanish.application.port.VanishRecords;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The jOOQ-backed {@link VanishRecords} over the generated {@code PLAYER_VANISH} table, one row per player.
 *
 * <p>A save is a conditional update that only touches a row written no later than the new state, so the newer of two
 * writes wins whichever lands first, and the comparison happens inside the one statement. A player with no row yet gets
 * an insert. Two backends inserting the same new player at once lose one insert to the primary key; that loser then
 * settles by the same conditional update.
 *
 * <p>A vanish or reveal replaces the whole row and clears the departure. A departure, a sighting or a release only
 * touches a row that still says vanished on the server that writes it, and an arrival moves a vanished row to its new
 * server, so a late write from the backend a player left changes nothing.
 */
public final class JooqVanishRecords extends JooqRepository implements VanishRecords {

    private static final short VANISHED = 1;
    private static final short VISIBLE = 0;

    /** Well under the smallest bound-parameter limit of the four backends, so one sighting batch is one statement. */
    private static final int BATCH = 500;

    public JooqVanishRecords(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<StoredVanish> find(UUID player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.selectFrom(PLAYER_VANISH)
                .where(PLAYER_VANISH.PLAYER_UUID.eq(player.toString()))
                .fetchOptional()
                .map(JooqVanishRecords::toStored));
    }

    @Override
    public void save(UUID player, StoredVanish state) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(state, "state");
        if (write(dsl -> updateIfNotNewer(dsl, player, state)) > 0) {
            return;
        }
        if (read(dsl -> dsl.fetchExists(PLAYER_VANISH, PLAYER_VANISH.PLAYER_UUID.eq(player.toString())))) {
            return; // the kept row was written later than this state
        }
        try {
            write(dsl -> dsl.insertInto(PLAYER_VANISH)
                    .set(PLAYER_VANISH.PLAYER_UUID, player.toString())
                    .set(PLAYER_VANISH.VANISHED, state.vanished() ? VANISHED : VISIBLE)
                    .set(PLAYER_VANISH.VANISH_LEVEL, levelColumn(state))
                    .set(PLAYER_VANISH.LAST_SERVER, state.server().orElse(null))
                    .set(PLAYER_VANISH.UPDATED_AT, state.updatedAt().toEpochMilli())
                    .set(PLAYER_VANISH.SEEN_AT, millis(state.seenAt()))
                    .set(PLAYER_VANISH.DEPARTED_AT, millis(state.departedAt()))
                    .execute());
        } catch (PersistenceException raced) {
            // Another backend inserted the row between the existence check and this insert.
            write(dsl -> updateIfNotNewer(dsl, player, state));
        }
    }

    @Override
    public void markDeparted(UUID player, String server, Instant at) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(at, "at");
        write(dsl -> dsl.update(PLAYER_VANISH)
                .set(PLAYER_VANISH.DEPARTED_AT, at.toEpochMilli())
                .where(PLAYER_VANISH.PLAYER_UUID.eq(player.toString()))
                .and(heldBy(server))
                .execute());
    }

    @Override
    public void markArrived(UUID player, String server, Instant at) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(at, "at");
        write(dsl -> dsl.update(PLAYER_VANISH)
                .set(PLAYER_VANISH.LAST_SERVER, server)
                .set(PLAYER_VANISH.SEEN_AT, at.toEpochMilli())
                .setNull(PLAYER_VANISH.DEPARTED_AT)
                .where(PLAYER_VANISH.PLAYER_UUID.eq(player.toString()))
                .and(PLAYER_VANISH.VANISHED.eq(VANISHED))
                .execute());
    }

    @Override
    public void touch(Collection<UUID> players, String server, Instant at) {
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(at, "at");
        List<String> ids = players.stream().map(UUID::toString).toList();
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<String> batch = ids.subList(from, Math.min(ids.size(), from + BATCH));
            write(dsl -> dsl.update(PLAYER_VANISH)
                    .set(PLAYER_VANISH.SEEN_AT, at.toEpochMilli())
                    .where(PLAYER_VANISH.PLAYER_UUID.in(batch))
                    .and(heldBy(server))
                    .and(PLAYER_VANISH.DEPARTED_AT.isNull())
                    .execute());
        }
    }

    @Override
    public void releaseHeld(String server, Instant at) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(at, "at");
        write(dsl -> dsl.update(PLAYER_VANISH)
                .set(PLAYER_VANISH.DEPARTED_AT, at.toEpochMilli())
                .where(heldBy(server))
                .and(PLAYER_VANISH.DEPARTED_AT.isNull())
                .execute());
    }

    @Override
    public void releaseStale(String server, Instant startedAt) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(startedAt, "startedAt");
        long started = startedAt.toEpochMilli();
        write(dsl -> dsl.update(PLAYER_VANISH)
                .set(PLAYER_VANISH.DEPARTED_AT, started)
                .where(heldBy(server))
                .and(PLAYER_VANISH.DEPARTED_AT.isNull())
                .and(PLAYER_VANISH.SEEN_AT.isNull().or(PLAYER_VANISH.SEEN_AT.lt(started)))
                .execute());
    }

    private static Condition heldBy(String server) {
        return PLAYER_VANISH.VANISHED.eq(VANISHED).and(PLAYER_VANISH.LAST_SERVER.eq(server));
    }

    private static int updateIfNotNewer(DSLContext dsl, UUID player, StoredVanish state) {
        long at = state.updatedAt().toEpochMilli();
        return dsl.update(PLAYER_VANISH)
                .set(PLAYER_VANISH.VANISHED, state.vanished() ? VANISHED : VISIBLE)
                .set(PLAYER_VANISH.VANISH_LEVEL, levelColumn(state))
                .set(PLAYER_VANISH.LAST_SERVER, state.server().orElse(null))
                .set(PLAYER_VANISH.UPDATED_AT, at)
                .set(PLAYER_VANISH.SEEN_AT, millis(state.seenAt()))
                .set(PLAYER_VANISH.DEPARTED_AT, millis(state.departedAt()))
                .where(PLAYER_VANISH.PLAYER_UUID.eq(player.toString()))
                .and(PLAYER_VANISH.UPDATED_AT.le(at))
                .execute();
    }

    private static @Nullable Integer levelColumn(StoredVanish state) {
        return state.level().map(VanishLevel::level).orElse(null);
    }

    private static @Nullable Long millis(Optional<Instant> instant) {
        return instant.map(Instant::toEpochMilli).orElse(null);
    }

    private static Optional<Instant> instant(@Nullable Long millis) {
        return millis == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(millis));
    }

    private static StoredVanish toStored(Record row) {
        Instant at = Instant.ofEpochMilli(row.get(PLAYER_VANISH.UPDATED_AT));
        if (row.get(PLAYER_VANISH.VANISHED) == VISIBLE) {
            return StoredVanish.visible(at);
        }
        Integer level = row.get(PLAYER_VANISH.VANISH_LEVEL);
        return new StoredVanish(
                true,
                Optional.of(level == null ? VanishLevel.DEFAULT : VanishLevel.of(level)),
                Optional.ofNullable(row.get(PLAYER_VANISH.LAST_SERVER)),
                at,
                instant(row.get(PLAYER_VANISH.SEEN_AT)),
                instant(row.get(PLAYER_VANISH.DEPARTED_AT)));
    }
}
