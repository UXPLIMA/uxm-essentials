package com.uxplima.uxmessentials.testing;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/** A logger that prints nothing and keeps the warnings and failures it was handed, so a test can see a report. */
public final class QuietLog implements Logger {

    private final List<Throwable> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    @Override
    public void info(String message, Object... args) {}

    @Override
    public void warn(String message, Object... args) {
        warnings.add(message);
    }

    @Override
    public void error(String message, Throwable cause) {
        errors.add(cause);
    }

    @Override
    public void debug(String message, Object... args) {}

    public List<Throwable> errors() {
        return List.copyOf(errors);
    }

    /** The warning templates, unexpanded. */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
