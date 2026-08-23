package com.uxplima.uxmessentials.scoreboard.domain;

import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/** One candidate sidebar line with identity that remains stable while conditions hide neighbouring lines. */
public record SidebarLine(
        String id, String text, DisplayCondition condition, SidebarNumberFormat numberFormat, boolean hideWhenEmpty) {

    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9._:-]{1,64}");

    public SidebarLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(numberFormat, "numberFormat");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("sidebar line id must match " + ID.pattern() + ": " + id);
        }
    }
}
