package com.uxplima.uxmessentials.persistence.runtime;

import static com.uxplima.uxmessentials.persistence.jooq.tables.IdAllocator.ID_ALLOCATOR;

import java.util.Objects;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * Hands out ids for one sequence of the {@code id_allocator} table, a block at a time, so every writer on every server
 * sharing the database gets ids no other writer has. A reservation adds the block's size to the sequence's
 * {@code next_id} and reads it back in one short transaction of its own; the database serialises two reservations on
 * the row, so their blocks never overlap. One round trip serves a whole block, never one per id.
 *
 * <p>A reservation opens its own transaction, so it must not be called while the caller holds one: on SQLite the
 * plugin's single connection would be waiting on itself. A caller that writes the ids inside a transaction reserves
 * them first.
 *
 * <p>A writer that does not draw from the allocator (a server still on an older release, or one rolled back to it)
 * can take ids ahead of it. {@link #windPast} moves the sequence beyond every id already written, so a writer that met
 * such an id clashes once instead of on every block until the allocator passes them by itself.
 */
@NullMarked
public final class IdBlocks {

    private final DSLContext dsl;
    private final String sequence;

    public IdBlocks(DSLContext dsl, String sequence) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.sequence = Objects.requireNonNull(sequence, "sequence");
    }

    /**
     * Reserve {@code count} consecutive ids and return the first. Ids a caller reserves and never writes are skipped
     * for good.
     *
     * @throws IllegalStateException when the sequence has no row, which the migrations always write
     */
    public long reserve(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        return Transactions.inTransaction(dsl, tx -> {
            int updated = tx.update(ID_ALLOCATOR)
                    .set(ID_ALLOCATOR.NEXT_ID, ID_ALLOCATOR.NEXT_ID.plus(count))
                    .where(ID_ALLOCATOR.SEQUENCE_NAME.eq(sequence))
                    .execute();
            if (updated != 1) {
                throw new IllegalStateException("no id sequence named " + sequence);
            }
            Long next = tx.select(ID_ALLOCATOR.NEXT_ID)
                    .from(ID_ALLOCATOR)
                    .where(ID_ALLOCATOR.SEQUENCE_NAME.eq(sequence))
                    .fetchOne(ID_ALLOCATOR.NEXT_ID);
            return Objects.requireNonNull(next, "next_id") - count;
        });
    }

    /**
     * Move the sequence past the highest {@code id} in {@code table}, never back. The comparison is a {@code CASE}
     * because SQLite has no {@code GREATEST}.
     */
    public void windPast(Table<?> table, Field<Long> id) {
        Transactions.inTransaction(dsl, tx -> {
            Long highest = tx.select(DSL.max(id)).from(table).fetchOne(0, Long.class);
            long floor = (highest == null ? 0L : highest) + 1;
            tx.update(ID_ALLOCATOR)
                    .set(
                            ID_ALLOCATOR.NEXT_ID,
                            DSL.when(ID_ALLOCATOR.NEXT_ID.gt(floor), ID_ALLOCATOR.NEXT_ID)
                                    .otherwise(DSL.val(floor)))
                    .where(ID_ALLOCATOR.SEQUENCE_NAME.eq(sequence))
                    .execute();
            return null;
        });
    }
}
