package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationExemptSeen.MODERATION_EXEMPT_SEEN;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ExemptionSightings;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ {@link ExemptionSightings} over {@code moderation_exempt_seen}, one row per player and backend. A sighting
 * updates the row only when it is not older than it, then inserts one when there was none, so a late write never
 * replaces a newer reading.
 */
@NullMarked
final class JooqExemptionSightings extends JooqRepository implements ExemptionSightings {

    private static final short HELD = 1;
    private static final short NOT_HELD = 0;

    JooqExemptionSightings(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void record(UUID player, String server, boolean held, Instant at) {
        String id = Objects.requireNonNull(player, "player").toString();
        Objects.requireNonNull(server, "server");
        long seenAt = Objects.requireNonNull(at, "at").toEpochMilli();
        short value = held ? HELD : NOT_HELD;
        int unused = write(dsl -> {
            int updated = dsl.update(MODERATION_EXEMPT_SEEN)
                    .set(MODERATION_EXEMPT_SEEN.HELD, value)
                    .set(MODERATION_EXEMPT_SEEN.SEEN_AT, seenAt)
                    .where(MODERATION_EXEMPT_SEEN.PLAYER_UUID.eq(id))
                    .and(MODERATION_EXEMPT_SEEN.SERVER.eq(server))
                    .and(MODERATION_EXEMPT_SEEN.SEEN_AT.le(seenAt))
                    .execute();
            if (updated == 0) {
                // No row yet, or a newer one, which the insert leaves alone.
                dsl.insertInto(MODERATION_EXEMPT_SEEN)
                        .set(MODERATION_EXEMPT_SEEN.PLAYER_UUID, id)
                        .set(MODERATION_EXEMPT_SEEN.SERVER, server)
                        .set(MODERATION_EXEMPT_SEEN.HELD, value)
                        .set(MODERATION_EXEMPT_SEEN.SEEN_AT, seenAt)
                        .onConflictDoNothing()
                        .execute();
            }
            return updated;
        });
    }

    @Override
    public boolean heldAnywhere(UUID player) {
        String id = Objects.requireNonNull(player, "player").toString();
        return read(dsl -> dsl.fetchExists(
                MODERATION_EXEMPT_SEEN,
                MODERATION_EXEMPT_SEEN.PLAYER_UUID.eq(id).and(MODERATION_EXEMPT_SEEN.HELD.eq(HELD))));
    }
}
