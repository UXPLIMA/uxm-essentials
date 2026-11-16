package com.uxplima.uxmessentials.persistence.runtime;

import java.sql.SQLException;

import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.jspecify.annotations.NullMarked;

/**
 * Recognises a write refused by a constraint, so a store that takes its keys as {@code max(id)+1} can tell a key
 * another writer took first from a real failure and try again with a fresh key.
 */
@NullMarked
public final class IntegrityViolation {

    private static final String SQL_STATE_CLASS = "23";

    private IntegrityViolation() {}

    /**
     * True when {@code failure} or one of its causes is a key or other constraint clash: SQLState class 23, or the
     * class jOOQ reads from SQLite's vendor code, which carries no SQLState of its own.
     */
    public static boolean causedBy(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessException access
                    && access.sqlStateClass() == SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION) {
                return true;
            }
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && state.startsWith(SQL_STATE_CLASS)) {
                    return true;
                }
            }
        }
        return false;
    }
}
