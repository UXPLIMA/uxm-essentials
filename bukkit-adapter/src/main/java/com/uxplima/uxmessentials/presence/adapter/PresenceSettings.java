package com.uxplima.uxmessentials.presence.adapter;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The presence context's typed config, read once from the module's scoped {@code modules.presence} subtree at
 * wiring time. It bounds the idle threshold (how long a player must be still before the sweep flips them to
 * AFK) and the sweep interval (how often the auto-AFK scan runs). Each reads against a sane default so an
 * operator who declares nothing still gets working behaviour.
 *
 * <p>A non-positive idle threshold is honoured by the domain as "never idle", so an operator switches auto-AFK
 * off by setting it to zero — manual {@code /afk} still works.
 */
@NullMarked
public final class PresenceSettings {

    private static final long DEFAULT_IDLE_THRESHOLD_SECONDS = 300L;
    private static final long DEFAULT_SWEEP_SECONDS = 15L;

    private final Duration idleThreshold;
    private final Duration sweepInterval;

    public PresenceSettings(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        this.idleThreshold =
                Duration.ofSeconds(config.getLong("afk-idle-threshold-seconds", DEFAULT_IDLE_THRESHOLD_SECONDS));
        this.sweepInterval = Duration.ofSeconds(config.getLong("afk-sweep-seconds", DEFAULT_SWEEP_SECONDS));
    }

    /** How long a player must be idle before the auto-AFK sweep flips them to AFK. */
    public Duration idleThreshold() {
        return idleThreshold;
    }

    /** How often the auto-AFK sweep scans the presence map. */
    public Duration sweepInterval() {
        return sweepInterval;
    }
}
