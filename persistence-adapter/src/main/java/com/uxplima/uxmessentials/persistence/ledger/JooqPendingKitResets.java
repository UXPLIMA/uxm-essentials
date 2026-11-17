package com.uxplima.uxmessentials.persistence.ledger;

import static com.uxplima.uxmessentials.persistence.jooq.tables.KitPendingResets.KIT_PENDING_RESETS;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.PendingKitResets;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link PendingKitResets} over the {@code kit_pending_resets} table, one row per kept reset. A take
 * deletes each row on its own and returns only the resets whose delete removed a row, so two takes at once hand each
 * reset to one of them.
 */
@NullMarked
public final class JooqPendingKitResets extends JooqRepository implements PendingKitResets {

    private final Clock clock;

    public JooqPendingKitResets(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void keep(UUID player, String kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kit, "kit");
        long now = clock.millis();
        write(dsl -> dsl.insertInto(KIT_PENDING_RESETS)
                .set(KIT_PENDING_RESETS.ID, UUID.randomUUID().toString())
                .set(KIT_PENDING_RESETS.PLAYER, player.toString())
                .set(KIT_PENDING_RESETS.KIT, kit)
                .set(KIT_PENDING_RESETS.CREATED_AT, now)
                .execute());
    }

    @Override
    public Set<String> take(UUID player) {
        Objects.requireNonNull(player, "player");
        List<Record2<String, String>> rows = read(dsl -> dsl.select(KIT_PENDING_RESETS.ID, KIT_PENDING_RESETS.KIT)
                .from(KIT_PENDING_RESETS)
                .where(KIT_PENDING_RESETS.PLAYER.eq(player.toString()))
                .orderBy(KIT_PENDING_RESETS.CREATED_AT, KIT_PENDING_RESETS.ID)
                .fetch());
        Set<String> taken = new LinkedHashSet<>();
        for (Record2<String, String> row : rows) {
            String id = row.value1();
            boolean removed = write(dsl -> dsl.deleteFrom(KIT_PENDING_RESETS)
                            .where(KIT_PENDING_RESETS.ID.eq(id))
                            .execute()
                    == 1);
            if (removed) {
                taken.add(row.value2());
            }
        }
        return taken;
    }
}
