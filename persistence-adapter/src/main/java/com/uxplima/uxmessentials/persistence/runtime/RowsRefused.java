package com.uxplima.uxmessentials.persistence.runtime;

import java.sql.SQLException;

import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.jspecify.annotations.NullMarked;

/**
 * Recognises a write the database refused because of the rows themselves: a value it cannot hold (SQLState class 22)
 * or a constraint they break (class 23). Writing the same rows again changes nothing, so a caller that retries
 * transient failures must not retry these unchanged. A lost connection, a timeout or a deadlock is not one of these.
 */
@NullMarked
public final class RowsRefused {

    private static final String DATA_EXCEPTION = "22";

    private RowsRefused() {}

    /** True when {@code failure} or one of its causes is a data exception or an integrity violation. */
    public static boolean causedBy(Throwable failure) {
        if (IntegrityViolation.causedBy(failure)) {
            return true;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessException access
                    && access.sqlStateClass() == SQLStateClass.C22_DATA_EXCEPTION) {
                return true;
            }
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && state.startsWith(DATA_EXCEPTION)) {
                    return true;
                }
            }
        }
        return false;
    }
}
