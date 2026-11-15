package com.uxplima.uxmessentials.shared.application.ledger;

import java.util.Locale;
import java.util.Objects;

/**
 * The stored name of a cooldown stamp. The PDC copy can only hold {@code [a-z0-9._-]} in a key, so the database copy
 * stores the same folded name: a stamp carried from PDC into the database then lands under the exact scope the
 * network cooldowns read.
 */
public final class CooldownScope {

    private CooldownScope() {}

    /** {@code name} lower cased, with every character outside {@code [a-z0-9._-]} folded to {@code _}. */
    public static String normalize(String name) {
        Objects.requireNonNull(name, "name");
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean legal = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            out.append(legal ? c : '_');
        }
        return out.toString();
    }
}
