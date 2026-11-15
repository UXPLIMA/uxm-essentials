package com.uxplima.uxmessentials.testing;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/** A {@link Logger} that keeps the message of every error it was handed and drops the rest. */
public final class RecordingLogger implements Logger {

    private final List<String> errors = new ArrayList<>();

    @Override
    public void info(String message, Object... args) {}

    @Override
    public void warn(String message, Object... args) {}

    @Override
    public void error(String message, Throwable cause) {
        errors.add(message);
    }

    @Override
    public void debug(String message, Object... args) {}

    public List<String> errors() {
        return List.copyOf(errors);
    }
}
