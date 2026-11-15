package com.uxplima.uxmessentials.shared.adapter.outbound.log;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link Logger} implementation over the plugin's SLF4J logger (obtained from the Paper plugin in
 * bootstrap). Operator-facing diagnostics flow through here rather than the player-facing
 * {@code MessageKey} catalog; the {@code {}} placeholders are expanded by SLF4J, so application code
 * never imports SLF4J directly and the kernel stays infrastructure-free.
 */
@NullMarked
public final class Slf4jLogger implements Logger {

    private final org.slf4j.Logger delegate;

    public Slf4jLogger(org.slf4j.Logger delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void info(String message, Object... args) {
        delegate.info(message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        delegate.warn(message, args);
    }

    @Override
    public void error(String message, Throwable cause) {
        delegate.error(message, cause);
    }

    @Override
    public void debug(String message, Object... args) {
        delegate.debug(message, args);
    }
}
