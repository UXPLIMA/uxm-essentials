package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/** The log lines written, warnings and errors kept apart so a test can say a failure was reported. */
final class RecordingLog implements Logger {

    final List<String> warnings = new CopyOnWriteArrayList<>();
    final List<String> errors = new CopyOnWriteArrayList<>();

    @Override
    public void info(String message, Object... args) {}

    @Override
    public void warn(String message, Object... args) {
        warnings.add(message);
    }

    @Override
    public void error(String message, Throwable cause) {
        errors.add(message);
    }

    @Override
    public void debug(String message, Object... args) {}
}
