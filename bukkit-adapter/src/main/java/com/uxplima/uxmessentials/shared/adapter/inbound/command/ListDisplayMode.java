package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * How a list command ({@code /warp list}, {@code /home}, {@code /kit list}) presents its entries to a player:
 * the paginated browse {@link #GUI} (the historical default) or the clickable {@link #CHAT} list. An operator
 * picks per module via the {@code list-display} key; an unrecognised value falls back to {@link #GUI} so a
 * typo never disables the command.
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
        return from(config, CONFIG_KEY);
    }

    /**
     * Resolve the mode named at {@code key} in {@code config}, defaulting to {@link #GUI}. The same {@code gui}
     * | {@code chat} vocabulary is reused by sibling knobs such as the kits context's {@code showkit-display},
     * which selects whether {@code /kit show} previews a kit in a GUI or in chat.
     */
    public static ListDisplayMode from(ConfigStore config, String key) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(key, "key");
        String raw = config.getString(key, "gui").trim().toLowerCase(Locale.ROOT);
        return "chat".equals(raw) ? CHAT : GUI;
    }
}
