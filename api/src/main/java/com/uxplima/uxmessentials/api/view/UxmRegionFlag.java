package com.uxplima.uxmessentials.api.view;

import java.util.Objects;

/**
 * One flag a region has set, as a name and a rendered value.
 *
 * <p>A string rather than a typed value because WorldGuard's flag registry is open: a plugin can register a flag of
 * any type it likes, and there is no set of types this could enumerate honestly. A state flag reads {@code ALLOW}
 * or {@code DENY}, a text flag reads its text, and a flag with nothing printable reads as empty.
 *
 * <p>Only the flags a region actually sets appear. A flag left unset is not listed, which is the same distinction
 * WorldGuard itself makes between "denied here" and "not decided here".
 *
 * @param name the flag's registered name, as {@code /rg flag} spells it
 * @param value the current value rendered as text, or empty when the flag carries nothing printable
 */
public record UxmRegionFlag(String name, String value) {

    public UxmRegionFlag {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
    }
}
