package com.uxplima.uxmessentials.guard.domain;

/**
 * The outcome of a check. {@code UNSET} means no region said anything, which the caller treats as allowed:
 * a region without rules does nothing.
 */
public enum Verdict {
    ALLOW,
    DENY,
    UNSET;

    /** True for anything but {@code DENY}. */
    public boolean allowed() {
        return this != DENY;
    }

    public static Verdict of(Effect effect) {
        return effect == Effect.ALLOW ? ALLOW : DENY;
    }
}
