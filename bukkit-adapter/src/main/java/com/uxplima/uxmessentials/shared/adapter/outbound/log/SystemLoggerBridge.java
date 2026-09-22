package com.uxplima.uxmessentials.shared.adapter.outbound.log;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The library's {@link System.Logger}, written into this plugin's own {@link Logger} port.
 *
 * <p>uxmLib logs through the JDK's logger because it cannot know any plugin's port, and a line it writes for
 * this plugin belongs in the same place as every other line this plugin writes. So the line is formatted
 * here, with {@link MessageFormat} as the JDK logger's contract says, and handed to the port finished: the
 * port's own {@code {}} placeholders never meet a {@code {0}}.
 *
 * <p>Every level is loggable. What an operator sees is the port's decision, made once, and a second filter
 * in front of it would be a second place to look when a line goes missing.
 */
@NullMarked
public final class SystemLoggerBridge implements System.Logger {

    private final String name;
    private final Logger port;

    public SystemLoggerBridge(String name, Logger port) {
        this.name = Objects.requireNonNull(name, "name");
        this.port = Objects.requireNonNull(port, "port");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isLoggable(Level level) {
        Objects.requireNonNull(level, "level");
        return true;
    }

    @Override
    public void log(
            Level level, @Nullable ResourceBundle bundle, @Nullable String message, @Nullable Throwable thrown) {
        Objects.requireNonNull(level, "level");
        write(level, message == null ? "" : message, thrown);
    }

    @Override
    public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... params) {
        Objects.requireNonNull(level, "level");
        String text = format == null ? "" : format;
        write(level, params == null || params.length == 0 ? text : MessageFormat.format(text, params), null);
    }

    private void write(Level level, String line, @Nullable Throwable thrown) {
        switch (level) {
            case ERROR -> port.error(line, thrown == null ? new IllegalStateException(line) : thrown);
            case WARNING -> {
                if (thrown == null) {
                    port.warn(line);
                } else {
                    port.warn(line + ": {}", thrown);
                }
            }
            case INFO -> port.info(line);
            default -> port.debug(line);
        }
    }
}
