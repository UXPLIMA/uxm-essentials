package com.uxplima.uxmessentials.shared.display;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The colour every theme role ships with, as {@code #rrggbb}. The backend reads these under the operator's theme file,
 * and the proxy, which runs on its own machine and cannot read that file, draws its role tags in them, so a line
 * written for both reads the same by default. One table, so the two cannot drift apart.
 */
public final class ShippedColours {

    private static final Map<String, String> ROLES = roles0();

    private ShippedColours() {}

    /** Each role's shipped colour, keyed by role name. */
    public static Map<String, String> roles() {
        return ROLES;
    }

    private static Map<String, String> roles0() {
        Map<String, String> colours = new LinkedHashMap<>();
        colours.put("accent", "#38b6ff");
        colours.put("value", "#8fd9ff");
        colours.put("body", "#ffffff");
        colours.put("subtext", "#dde8f0");
        colours.put("muted", "#93a4b3");
        colours.put("dim", "#6b7886");
        colours.put("icon", "#8a93a1");
        colours.put("crumb", "#565f6b");
        colours.put("good", "#5be38c");
        colours.put("bad", "#ff6b6b");
        colours.put("warn", "#ffc93c");
        colours.put("money", "#ffc93c");
        colours.put("level", "#ffc93c");
        colours.put("cta", "#ffc93c");
        colours.put("info", "#4fd6e8");
        colours.put("rank", "#b68cff");
        colours.put("event", "#ff8fd0");
        // A plain title carries the reading colour rather than a colour of its own.
        colours.put("title", "#ffffff");
        // The lighter half of each ramp, used when the file names no wheel to take a neighbour from.
        colours.put("emerald", "#45d9a6");
        colours.put("rose", "#ff7aa8");
        colours.put("yellow", "#ffe15c");
        return Map.copyOf(colours);
    }
}
