package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpShuffle;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record2;
import org.jooq.Result;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ {@link PlayerWarpShuffle}: rewrites the {@code random_sort} column the RANDOM browse pages by.
 *
 * <p>A warp's place for a round is a hash of its id and the round, not a draw from a random source. Servers that
 * share the database therefore write the same value for the same round, so whichever server reaches a round first
 * deals it and the others find every row already in place and write nothing. A server that reaches it at the same
 * moment writes the same values again, which changes nothing. The hash is computed here rather than in SQL because no
 * single random or hash function is portable across SQLite, MySQL, MariaDB and PostgreSQL.
 *
 * <p>The table is walked by id in chunks, each read and its updates in a short transaction of its own, so a large
 * table never holds a write lock for the whole pass. A browse that reads during a pass sees the rows already dealt in
 * their new places and the rest in their old ones.
 */
@NullMarked
public final class JooqPlayerWarpShuffle extends JooqRepository implements PlayerWarpShuffle {

    private static final int CHUNK = 500;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private final int chunk;

    public JooqPlayerWarpShuffle(DSLContext dsl) {
        this(dsl, CHUNK);
    }

    JooqPlayerWarpShuffle(DSLContext dsl, int chunk) {
        super(dsl);
        if (chunk < 1) {
            throw new IllegalArgumentException("chunk must be at least 1: " + chunk);
        }
        this.chunk = chunk;
    }

    @Override
    public int shuffle(long round) {
        long seed = mix(round);
        int moved = 0;
        long after = Long.MIN_VALUE;
        while (true) {
            long from = after;
            Result<Record2<Long, Long>> rows = read(dsl -> dsl.select(PLAYER_WARPS.ID, PLAYER_WARPS.RANDOM_SORT)
                    .from(PLAYER_WARPS)
                    .where(PLAYER_WARPS.ID.gt(from))
                    .orderBy(PLAYER_WARPS.ID.asc())
                    .limit(chunk)
                    .fetch());
            if (rows.isEmpty()) {
                return moved;
            }
            moved += deal(rows, seed);
            after = rows.get(rows.size() - 1).value1();
            if (rows.size() < chunk) {
                return moved;
            }
        }
    }

    /** Write the place of every row in the chunk that is not already at it; returns how many were written. */
    private int deal(Result<Record2<Long, Long>> rows, long seed) {
        List<long[]> stale = new ArrayList<>();
        for (Record2<Long, Long> row : rows) {
            long id = row.value1();
            long place = place(id, seed);
            if (row.value2() != place) {
                stale.add(new long[] {id, place});
            }
        }
        if (stale.isEmpty()) {
            return 0;
        }
        write(dsl -> {
            List<Query> updates = new ArrayList<>(stale.size());
            for (long[] row : stale) {
                updates.add(dsl.update(PLAYER_WARPS)
                        .set(PLAYER_WARPS.RANDOM_SORT, row[1])
                        .where(PLAYER_WARPS.ID.eq(row[0])));
            }
            return dsl.batch(updates).execute();
        });
        return stale.size();
    }

    /** One warp's place in a round. Distinct ids get distinct places, because both steps are bijections. */
    private static long place(long id, long seed) {
        return mix(seed + id * GOLDEN);
    }

    /** The SplitMix64 finalizer: spreads every input bit over the whole output. */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
