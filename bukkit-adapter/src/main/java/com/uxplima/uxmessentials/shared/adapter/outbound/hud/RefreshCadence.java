package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * How often a HUD module re-renders every viewer, read from {@code refresh-ticks}. The top-level key is the global
 * cadence. The single-block shape ({@code scoreboard { … }}, {@code tablist { … }}) also carries a key of its own,
 * which up to 0.10.0 was the only one that shape read, so both shipped at 20 and an operator may have changed
 * either. {@link #resolve} reads the one that was changed, so an old file keeps its cadence and the global key works
 * as its comment says.
 */
@NullMarked
public final class RefreshCadence {

    private static final long DEFAULT_TICKS = 20L;
    private static final long MILLIS_PER_TICK = 50L;
    private static final String KEY = "refresh-ticks";

    private RefreshCadence() {}

    /** The cadence one {@code refresh-ticks} node names; absent or non-positive is one second. */
    public static Duration of(ConfigurationNode node) {
        return toDuration(node.getLong(DEFAULT_TICKS));
    }

    /**
     * The cadence of a single-block file: the top-level key unless only the block's own key was moved off the
     * default. When both were changed and disagree, the top-level key wins and the conflict is logged.
     */
    public static Duration resolve(ConfigurationNode root, ConfigurationNode block, String blockName, Logger log) {
        Objects.requireNonNull(log, "log");
        ConfigurationNode top = root.node(KEY);
        ConfigurationNode own = block.node(KEY);
        if (own.virtual()) {
            return of(top);
        }
        if (top.virtual()) {
            return of(own);
        }
        long topTicks = top.getLong(DEFAULT_TICKS);
        long ownTicks = own.getLong(DEFAULT_TICKS);
        if (topTicks == DEFAULT_TICKS && ownTicks != DEFAULT_TICKS) {
            return toDuration(ownTicks);
        }
        if (topTicks != DEFAULT_TICKS && ownTicks != DEFAULT_TICKS && topTicks != ownTicks) {
            log.warn("event=refresh_ticks_conflict used={} ignored={}", KEY, blockName + "." + KEY);
        }
        return toDuration(topTicks);
    }

    private static Duration toDuration(long ticks) {
        return Duration.ofMillis((ticks > 0L ? ticks : DEFAULT_TICKS) * MILLIS_PER_TICK);
    }
}
