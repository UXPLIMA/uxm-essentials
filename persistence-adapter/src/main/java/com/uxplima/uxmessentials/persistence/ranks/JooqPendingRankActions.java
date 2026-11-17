package com.uxplima.uxmessentials.persistence.ranks;

import static com.uxplima.uxmessentials.persistence.jooq.tables.RankPendingActions.RANK_PENDING_ACTIONS;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.ranks.application.port.PendingRankActions;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link PendingRankActions} over the {@code rank_pending_actions} table, one row per kept line.
 *
 * <p>A kept batch continues its owner's numbering, so the lines come back in the order they were kept even when two
 * batches land in the same millisecond. A take deletes each row on its own and returns only the lines whose delete
 * removed a row, so two servers taking at once, or a quick rejoin, run each line once on any isolation level.
 */
@NullMarked
public final class JooqPendingRankActions extends JooqRepository implements PendingRankActions {

    private final Clock clock;

    public JooqPendingRankActions(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void keep(UUID player, List<String> lines) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            return;
        }
        String owner = player.toString();
        long now = clock.millis();
        write(dsl -> {
            Integer last = dsl.select(DSL.max(RANK_PENDING_ACTIONS.SEQ))
                    .from(RANK_PENDING_ACTIONS)
                    .where(RANK_PENDING_ACTIONS.OWNER.eq(owner))
                    .fetchOne(0, Integer.class);
            int next = last == null ? 0 : last + 1;
            for (String line : lines) {
                dsl.insertInto(RANK_PENDING_ACTIONS)
                        .set(RANK_PENDING_ACTIONS.ID, UUID.randomUUID().toString())
                        .set(RANK_PENDING_ACTIONS.OWNER, owner)
                        .set(RANK_PENDING_ACTIONS.CREATED_AT, now)
                        .set(RANK_PENDING_ACTIONS.SEQ, next++)
                        .set(RANK_PENDING_ACTIONS.ACTION_LINE, line)
                        .execute();
            }
            return null;
        });
    }

    @Override
    public List<String> take(UUID player) {
        Objects.requireNonNull(player, "player");
        List<Record2<String, String>> rows =
                read(dsl -> dsl.select(RANK_PENDING_ACTIONS.ID, RANK_PENDING_ACTIONS.ACTION_LINE)
                        .from(RANK_PENDING_ACTIONS)
                        .where(RANK_PENDING_ACTIONS.OWNER.eq(player.toString()))
                        .orderBy(RANK_PENDING_ACTIONS.SEQ, RANK_PENDING_ACTIONS.CREATED_AT, RANK_PENDING_ACTIONS.ID)
                        .fetch());
        List<String> taken = new ArrayList<>(rows.size());
        for (Record2<String, String> row : rows) {
            String id = row.value1();
            boolean removed = write(dsl -> dsl.deleteFrom(RANK_PENDING_ACTIONS)
                            .where(RANK_PENDING_ACTIONS.ID.eq(id))
                            .execute()
                    == 1);
            if (removed) {
                taken.add(row.value2());
            }
        }
        return taken;
    }
}
