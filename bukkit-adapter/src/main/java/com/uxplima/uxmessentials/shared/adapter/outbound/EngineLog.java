package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.common.Log;
import org.jspecify.annotations.NullMarked;

/**
 * The one line between this plugin's {@link Logger} port and the {@link Log} port uxmLib's menu engine takes.
 *
 * <p>The two interfaces carry the same four methods and the same {@code {}} placeholder style, so the adapter
 * forwards and decides nothing. It exists because the plugin keeps its own port: 744 files import {@link Logger}
 * and moving them onto the library's type would buy nothing. Only the engine's call sites cross here.
 */
@NullMarked
public final class EngineLog {

    private EngineLog() {}

    /** A {@link Log} that writes through {@code logger}. */
    public static Log of(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return new Log() {

            @Override
            public void info(String message, Object... args) {
                logger.info(message, args);
            }

            @Override
            public void warn(String message, Object... args) {
                logger.warn(message, args);
            }

            @Override
            public void error(String message, Throwable cause) {
                logger.error(message, cause);
            }

            @Override
            public void debug(String message, Object... args) {
                logger.debug(message, args);
            }
        };
    }
}
