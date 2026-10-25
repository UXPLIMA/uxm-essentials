package com.uxplima.uxmessentials.bossbar.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * One bar an operator wrote. The text, colour, style and progress are sources: each may hold a placeholder or an
 * animation, and the text may be a catalogue line, so the adapter fills them for each reader on every refresh.
 *
 * @param id the name commands and announcements use for the bar: letters, digits, dashes and underscores
 * @param text the words on the bar
 * @param color a boss bar colour name, or a source that gives one
 * @param style a boss bar style name ({@code PROGRESS} or {@code NOTCHED_6/10/12/20}), or a source that gives one
 * @param progress how full the bar is, 0 to 100, or {@code time} to drain it over its window
 * @param condition who sees the bar
 * @param window when the bar is up; empty for a bar that is always up
 * @param announcementOnly whether the bar is up only while a command announces it
 */
public record BossBarDefinition(
        String id,
        String text,
        String color,
        String style,
        String progress,
        DisplayCondition condition,
        Optional<BossBarWindow> window,
        boolean announcementOnly) {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]+");

    public BossBarDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(window, "window");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("a boss bar id is letters, digits, dashes and underscores: " + id);
        }
    }
}
