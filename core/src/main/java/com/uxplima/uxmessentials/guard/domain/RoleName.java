package com.uxplima.uxmessentials.guard.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A region role. Four are built in; the config adds more. Roles are per region: a player is a member of one
 * region and nothing in the next.
 *
 * @param value the canonical lower case name
 */
public record RoleName(String value) {

    private static final Pattern SHAPE = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    public static final RoleName OWNER = new RoleName("owner");
    public static final RoleName MANAGER = new RoleName("manager");
    public static final RoleName MEMBER = new RoleName("member");
    public static final RoleName GUEST = new RoleName("guest");

    /** The built-in roles, strongest first. */
    public static final List<RoleName> BUILT_IN = List.of(OWNER, MANAGER, MEMBER, GUEST);

    public RoleName {
        Objects.requireNonNull(value, "value");
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("not a role name: " + value);
        }
    }

    /** Parse raw input, trimming and lower-casing it. */
    public static RoleName of(String raw) {
        return new RoleName(Objects.requireNonNull(raw, "raw").strip().toLowerCase(Locale.ROOT));
    }

    /** Owners and managers may manage the members and guests of their own region. */
    public boolean manages() {
        return equals(OWNER) || equals(MANAGER);
    }

    @Override
    public String toString() {
        return value;
    }
}
