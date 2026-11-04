package com.uxplima.uxmessentials.guard.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The name of a node in the {@link ActionTree}: dot separated segments such as {@code use.door} or
 * {@code world.explosion.creeper}, normalised to lower case.
 *
 * <p>The dots are the tree: {@code use.door}'s parent is {@code use}. A segment is letters, digits and
 * dashes, so an id typed in a command or written by another plugin always parses the same way.
 *
 * @param value the canonical lower case id
 */
public record ActionId(String value) implements Comparable<ActionId> {

    private static final Pattern SHAPE = Pattern.compile("[a-z0-9][a-z0-9-]*(\\.[a-z0-9][a-z0-9-]*)*");

    /** Longest id accepted; a deeper tree is a mistake rather than a design. */
    public static final int MAX_LENGTH = 96;

    public ActionId {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("not an action id: " + value);
        }
    }

    /** Parse raw input, trimming and lower-casing it. */
    public static ActionId of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new ActionId(raw.strip().toLowerCase(Locale.ROOT));
    }

    /** The parent node's id, or {@code null} for a root node. */
    public @org.jspecify.annotations.Nullable ActionId parent() {
        int dot = value.lastIndexOf('.');
        return dot < 0 ? null : new ActionId(value.substring(0, dot));
    }

    /** True when this id is {@code other} or lies under it. */
    public boolean isWithin(ActionId other) {
        return value.equals(other.value) || value.startsWith(other.value + ".");
    }

    /** True for the actor-less {@code world.*} family that nature, not a player, performs. */
    public boolean isWorldAction() {
        return value.equals("world") || value.startsWith("world.");
    }

    @Override
    public int compareTo(ActionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
