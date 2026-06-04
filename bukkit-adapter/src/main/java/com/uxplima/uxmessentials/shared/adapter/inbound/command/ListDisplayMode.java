package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * How a bare list command ({@code /warps}, {@code /homes}, {@code /kits}) presents its entries to a player:
 * the paginated browse {@link #GUI} (the historical default) or the clickable {@link #CHAT} list (the same
 * output as the explicit {@code list} subcommand). An operator picks per module via the {@code list-display}
 * key; an unrecognised value falls back to {@link #GUI} so a typo never disables the command.
 *
 * <p>The mode is read from the live {@link ConfigStore} at command-execution time, not cached at construction,
 * so {@code /uxmess reload <module>} takes effect without a restart (CLAUDE.md atomic-reload rule).
 */
public enum ListDisplayMode {
    GUI,
    CHAT;

    /** The config key every list-bearing module reads, relative to its scoped subtree. */
    public static final String CONFIG_KEY = "list-display";

    /** Resolve the mode named at {@code list-display} in {@code config}, defaulting to {@link #GUI}. */
    public static ListDisplayMode from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        String raw = config.getString(CONFIG_KEY, "gui").trim().toLowerCase(Locale.ROOT);
        return "chat".equals(raw) ? CHAT : GUI;
    }
}
