package com.uxplima.uxmessentials.testing;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/** A {@link Logger} that keeps every error and warning it was handed and drops the rest. */
public final class RecordingLogger implements Logger {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    @Override
    public void info(String message, Object... args) {}

    @Override
    public void warn(String message, Object... args) {
        warnings.add(message + " " + java.util.Arrays.toString(args));
    }

    @Override
    public void error(String message, Throwable cause) {
        errors.add(message);
    }

    @Override
    public void debug(String message, Object... args) {}

    public List<String> errors() {
        return List.copyOf(errors);
    }

    /** Each warning's template followed by its arguments. */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
