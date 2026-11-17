package com.uxplima.uxmessentials.persistence.runtime;

import java.util.Objects;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.jspecify.annotations.NullMarked;

/**
 * A small transaction helper over a jOOQ {@link DSLContext}.
 *
 * <p>It runs a unit of work inside a single jOOQ transaction: the work commits if it returns normally
 * and rolls back if it throws. This is the seam the offline-row upsert relies on, the parent
 * {@code ensureUserExists} insert and the dependent write happen in the same transaction, so a foreign
 * key can never observe a missing parent.
 * jOOQ's {@code DataAccessException} is translated to the layer's {@link PersistenceException} so callers
 * never catch a jOOQ type. Transactions are expected to be millisecond-scale and must never wrap a
 * Bukkit API call.
 */
@NullMarked
public final class Transactions {

    private Transactions() {}

    /**
     * Run {@code work} in a transaction and return its result, committing on success and rolling back on
     * any exception. The transactional {@link DSLContext} passed to {@code work} must be the one used for
     * every statement inside the unit of work.
     */
    public static <T> T inTransaction(DSLContext dsl, Function<DSLContext, T> work) {
        Objects.requireNonNull(dsl, "dsl");
        Objects.requireNonNull(work, "work");
        try {
            return dsl.transactionResult(configuration -> work.apply(configuration.dsl()));
        } catch (DataAccessException cause) {
            throw new PersistenceException("transaction failed and was rolled back", cause);
        }
    }

    /**
     * Run {@code work} like {@link #inTransaction}, and run it again, up to {@code attempts} times in all, when the
     * database rolled it back to break a deadlock or a serialisation conflict (SQLSTATE class 40). Such a transaction
     * committed nothing, so running it again cannot apply it twice; any other failure is passed on at once. For short
     * units of work a player waits on, such as a payment, where losing a lock cycle to a long writer must not fail
     * the payment.
     */
    public static <T> T inTransactionRetrying(DSLContext dsl, int attempts, Function<DSLContext, T> work) {
        Objects.requireNonNull(dsl, "dsl");
        Objects.requireNonNull(work, "work");
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be at least 1: " + attempts);
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return dsl.transactionResult(configuration -> work.apply(configuration.dsl()));
            } catch (DataAccessException cause) {
                if (attempt >= attempts || cause.sqlStateClass() != SQLStateClass.C40_TRANSACTION_ROLLBACK) {
                    throw new PersistenceException("transaction failed and was rolled back", cause);
                }
            }
        }
    }
}
