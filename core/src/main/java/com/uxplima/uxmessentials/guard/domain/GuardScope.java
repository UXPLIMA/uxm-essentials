package com.uxplima.uxmessentials.guard.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Which servers load a region: one server ({@code server:<id>}) or every server of a shared set
 * ({@code set:<name>}). Lobby-1 to lobby-5 can share {@code set:lobby} and edit its regions from any of them.
 *
 * @param value the stored form
 */
public record GuardScope(String value) {

    private static final Pattern SHAPE = Pattern.compile("(server|set):[A-Za-z0-9_.-]{1,64}");

    public GuardScope {
        Objects.requireNonNull(value, "value");
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("not a scope: " + value);
        }
    }

    /** The scope of one server. */
    public static GuardScope server(String serverId) {
        return new GuardScope("server:" + serverId);
    }

    /** The scope of a shared set. */
    public static GuardScope set(String name) {
        return new GuardScope("set:" + name);
    }

    /** True for a shared set. */
    public boolean shared() {
        return value.startsWith("set:");
    }

    /** The server id or the set name. */
    public String name() {
        return value.substring(value.indexOf(':') + 1);
    }

    @Override
    public String toString() {
        return value;
    }
}
