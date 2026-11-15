package com.uxplima.uxmessentials.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/** Keeps every warning with its placeholders filled in, so a test can read what the operator was told. */
public final class RecordingLogger implements Logger {

    private final List<String> warnings = new ArrayList<>();

    public synchronized List<String> warnings() {
        return List.copyOf(warnings);
    }

    @Override
    public void info(String message, Object... args) {}

    @Override
    public synchronized void warn(String message, Object... args) {
        String line = message;
        for (Object arg : args) {
            line = line.replaceFirst("\\{}", Matcher.quoteReplacement(String.valueOf(arg)));
        }
        warnings.add(line);
    }

    @Override
    public void error(String message, Throwable cause) {}

    @Override
    public void debug(String message, Object... args) {}
}
