package com.uxplima.uxmessentials.communication.domain;

import java.util.List;
import java.util.Objects;

/**
 * One operator-authored info page: a command name ({@code rules}, {@code motd}, {@code info}) bound to the lines
 * shown when a player runs it. The lines are MiniMessage content the operator writes in {@code communication.conf}
 * (or an included text file) and are rendered, one Component per line, by the adapter — they are operator data,
 * never plugin {@code MessageKey}s, so they are not parity-checked.
 *
 * <p>The {@link #command} is the dynamic literal the module registers ({@code /rules} → this page). It is
 * normalised to lower case and validated as a single bare word so it forms a legal Brigadier literal; the
 * registry rejects two pages claiming the same command. A page may carry the {@code {player}} placeholder in its
 * lines, substituted per viewer before MiniMessage parses.
 *
 * @param command the bare command literal (no slash) that opens this page, lower case
 * @param lines the page body, one MiniMessage template per line
 */
public record InfoPage(String command, List<String> lines) {

    public InfoPage {
        Objects.requireNonNull(command, "command");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        String normalised = command.toLowerCase(java.util.Locale.ROOT);
        if (!normalised.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("info page command must be a single bare word: " + command);
        }
        command = normalised;
    }

    /** An info page named {@code command} with the given body lines. */
    public static InfoPage of(String command, List<String> lines) {
        return new InfoPage(command, lines);
    }

    /** The number of body lines on the page. */
    public int size() {
        return lines.size();
    }

    /** Whether the page has no body lines (a misconfigured or intentionally empty page). */
    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
