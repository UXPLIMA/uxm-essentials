package com.uxplima.uxmessentials.persistence.runtime;

import java.util.Objects;
import java.util.regex.Pattern;

import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogCreator;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway's log, without the warning MariaDB writes for every named primary key.
 *
 * <p>The migrations name their primary keys so the schema reads the same on every backend. MariaDB ignores the name
 * and says so, code 1280, once per table, and a first start printed seventy six such warnings: an operator read a wall
 * of WARN as a failed install. That one line is dropped. Every other line, every other warning included, reaches the
 * server log as before.
 *
 * <p>{@link FlywayMigrationRunner} hands Flyway an instance before it migrates.
 */
@NullMarked
public final class QuietFlywayLog implements LogCreator {

    private static final Pattern IGNORED_KEY_NAME = Pattern.compile("^DB: Name '[^']*' ignored for PRIMARY key\\.");

    @Override
    public Log createLogger(Class<?> source) {
        return around(new Slf4jLog(LoggerFactory.getLogger(source)));
    }

    /** {@code log}, passing every line through except the ignored primary key name. */
    static Log around(Log log) {
        Objects.requireNonNull(log, "log");
        return new Log() {
            @Override
            public boolean isDebugEnabled() {
                return log.isDebugEnabled();
            }

            @Override
            public void debug(String message) {
                log.debug(message);
            }

            @Override
            public void info(String message) {
                log.info(message);
            }

            @Override
            public void warn(String message) {
                if (!IGNORED_KEY_NAME.matcher(message).find()) {
                    log.warn(message);
                }
            }

            @Override
            public void error(String message) {
                log.error(message);
            }

            @Override
            public void error(String message, Exception failure) {
                log.error(message, failure);
            }

            @Override
            public void notice(String message) {
                log.notice(message);
            }
        };
    }

    /** Flyway's lines on the SLF4J logger named for the class that wrote them, as Flyway itself would on Paper. */
    private record Slf4jLog(Logger logger) implements Log {
        @Override
        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }

        @Override
        public void debug(String message) {
            logger.debug(message);
        }

        @Override
        public void info(String message) {
            logger.info(message);
        }

        @Override
        public void warn(String message) {
            logger.warn(message);
        }

        @Override
        public void error(String message) {
            logger.error(message);
        }

        @Override
        public void error(String message, Exception failure) {
            logger.error(message, failure);
        }

        @Override
        public void notice(String message) {
            logger.info(message);
        }
    }
}
