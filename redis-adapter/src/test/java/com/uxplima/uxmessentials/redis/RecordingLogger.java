package com.uxplima.uxmessentials.redis;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/** Test {@link Logger} that captures WARN lines so a test can assert the fail-degraded path logged. */
final class RecordingLogger implements Logger {

    private final List<String> warnings = new ArrayList<>();

    @Override
    public void info(String message, Object... args) {}

    @Override
    public void warn(String message, Object... args) {
        warnings.add(message);
    }

    @Override
    public void error(String message, Throwable cause) {}

    @Override
    public void debug(String message, Object... args) {}

    List<String> warnings() {
        return warnings;
    }
}
