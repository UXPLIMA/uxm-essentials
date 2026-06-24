package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

/**
 * How often a menu re-renders itself. When refresh is disabled the interval is ignored; when enabled it must be
 * at least one tick, since a zero or negative interval would mean an unbounded re-render loop.
 */
public record RefreshSpec(boolean enabled, int intervalTicks) {

    public RefreshSpec {
        if (enabled && intervalTicks < 1) {
            throw new IllegalArgumentException("intervalTicks must be >= 1 when refresh is enabled: " + intervalTicks);
        }
    }
}
