package com.uxplima.uxmessentials.shared.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Which servers of a network a thing belongs to: one server ({@code server:<id>}) or every server of a shared set
 * ({@code set:<name>}). Lobby-1 to lobby-5 can share {@code set:lobby} and edit its guard regions, holograms, NPCs
 * and main spawn from any of them. Something with no scope at all belongs to every server; that case is an absent
 * scope, never a value of this type, so stored rows written before scopes existed keep applying everywhere.
 *
 * @param value the stored form
 */
public record ServerScope(String value) {

    private static final Pattern SHAPE = Pattern.compile("(server|set):[A-Za-z0-9_.-]{1,64}");

    public ServerScope {
        Objects.requireNonNull(value, "value");
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("not a scope: " + value);
        }
    }

    /** True when {@code value} is a scope in its stored form. */
    public static boolean isScope(String value) {
        return SHAPE.matcher(Objects.requireNonNull(value, "value")).matches();
    }

    /** The scope of one server. */
    public static ServerScope server(String serverId) {
        return new ServerScope("server:" + serverId);
    }

    /** The scope of a shared set. */
    public static ServerScope set(String name) {
        return new ServerScope("set:" + name);
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
