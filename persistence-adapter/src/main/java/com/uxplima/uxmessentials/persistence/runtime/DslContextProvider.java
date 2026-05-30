package com.uxplima.uxmessentials.persistence.runtime;

import java.util.Objects;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the jOOQ {@link DSLContext} over a {@link DataSource} in the backend's dialect.
 *
 * <p>One {@code DSLContext} is created per enable and shared by every repository — it is thread-safe and
 * holds no connection itself; each operation borrows a pooled connection and returns it. The dialect is
 * the only backend-specific input, so the generated DSL renders correctly on SQLite, MySQL and
 * PostgreSQL from the same call sites. The provider knows nothing about specific tables; the generated
 * {@code Tables} constants and the repositories carry that.
 */
@NullMarked
public final class DslContextProvider {

    private DslContextProvider() {}

    /** Create a shared {@link DSLContext} bound to {@code dataSource}, rendering in {@code dialect}. */
    public static DSLContext create(DataSource dataSource, SQLDialect dialect) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(dialect, "dialect");
        return DSL.using(dataSource, dialect);
    }
}
