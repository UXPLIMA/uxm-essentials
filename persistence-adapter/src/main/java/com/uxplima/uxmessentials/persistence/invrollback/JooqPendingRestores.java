package com.uxplima.uxmessentials.persistence.invrollback;

import static com.uxplima.uxmessentials.persistence.jooq.tables.InvPendingRestores.INV_PENDING_RESTORES;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.PendingRestores;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.InvPendingRestoresRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link PendingRestores} over the {@code inv_pending_restores} table, one row per kept restore holding a
 * full copy of the chosen snapshot. A take deletes each row on its own and returns only the snapshots whose delete
 * removed a row, so two takes at once hand each restore to one of them.
 */
@NullMarked
public final class JooqPendingRestores extends JooqRepository implements PendingRestores {

    private final Clock clock;

    public JooqPendingRestores(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void keep(UUID player, Snapshot snapshot) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        long now = clock.millis();
        write(dsl -> dsl.insertInto(INV_PENDING_RESTORES)
                .set(INV_PENDING_RESTORES.ID, UUID.randomUUID().toString())
                .set(INV_PENDING_RESTORES.PLAYER, player.toString())
                .set(INV_PENDING_RESTORES.SNAPSHOT_ID, snapshot.id().asText())
                .set(INV_PENDING_RESTORES.CAUSE, snapshot.cause().name())
                .set(
                        INV_PENDING_RESTORES.SNAPSHOT_CREATED_AT,
                        snapshot.createdAt().toEpochMilli())
                .set(INV_PENDING_RESTORES.CONTENTS, SnapshotRows.encode(snapshot.contents()))
                .set(INV_PENDING_RESTORES.CREATED_AT, now)
                .execute());
    }

    @Override
    public List<Snapshot> take(UUID player) {
        Objects.requireNonNull(player, "player");
        List<InvPendingRestoresRecord> rows = read(dsl -> dsl.selectFrom(INV_PENDING_RESTORES)
                .where(INV_PENDING_RESTORES.PLAYER.eq(player.toString()))
                .orderBy(INV_PENDING_RESTORES.CREATED_AT, INV_PENDING_RESTORES.ID)
                .fetch());
        List<Snapshot> taken = new ArrayList<>();
        for (InvPendingRestoresRecord row : rows) {
            String id = row.getId();
            boolean removed = write(dsl -> dsl.deleteFrom(INV_PENDING_RESTORES)
                            .where(INV_PENDING_RESTORES.ID.eq(id))
                            .execute()
                    == 1);
            if (removed) {
                taken.add(Snapshot.of(
                        SnapshotId.parse(row.getSnapshotId()),
                        player,
                        SnapshotCause.valueOf(row.getCause()),
                        Instant.ofEpochMilli(row.getSnapshotCreatedAt()),
                        SnapshotRows.decode(row.getContents())));
            }
        }
        return taken;
    }
}
