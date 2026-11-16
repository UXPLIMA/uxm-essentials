package com.uxplima.uxmessentials.persistence.testing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.uxplima.uxmlib.storage.testing.RealDatabases;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs a repository test class on every backend the plugin supports.
 *
 * <p>A test takes a {@link TestDatabase} as a parameter of its {@code @BeforeEach} or test method and builds the
 * adapter over it. Every build runs the class on SQLite. The class carries the {@code real-database} tag, so the
 * {@code mysqlTest}, {@code mariadbTest} and {@code postgresqlTest} tasks run it again on that server in a container,
 * through the same pool, migrations and dialect an operator's server uses.
 *
 * <p>The backend is chosen per test JVM rather than per method, so a class keeps its plain {@code @Test} methods
 * and its {@code @BeforeEach}, and the one SQLite run stays in the ordinary build.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag(RealDatabases.TAG)
@ExtendWith(Backends.class)
public @interface EveryBackend {}
