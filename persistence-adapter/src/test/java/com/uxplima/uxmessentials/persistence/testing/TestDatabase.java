package com.uxplima.uxmessentials.persistence.testing;

import java.util.Objects;

import javax.sql.DataSource;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import org.jooq.DSLContext;

/**
 * A migrated database with no rows of its own, handed to one test. On SQLite it is a file of its own; on a server it
 * is the shared database of this test JVM, emptied before the test and given back the rows the migrations write.
 *
 * @param backend the backend the test runs on
 * @param dataSource where the test's connections come from: the plugin's own pool on a server
 * @param dsl the context a repository is built over, in the backend's dialect and with no table prefix
 */
public record TestDatabase(TestBackend backend, DataSource dataSource, DSLContext dsl) {

    public TestDatabase {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(dsl, "dsl");
    }

    /** The backend the plugin's persistence layer treats this database as. */
    public DatabaseBackend runtime() {
        return backend.runtime();
    }
}
