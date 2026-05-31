package com.uxplima.uxmessentials.moderation.adapter;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * Typed view over the {@code moderation.conf} subtree: the configured jails (name → world/coordinates) and
 * the per-jail countdown mode. A jail referenced by {@code /jail <player> <jail>} must exist here, and the
 * countdown mode decides whether a timed sentence is online-only (the default) or wall-clock
 * ({@code jail-countdown}). Read once at wire time from the module's scoped config.
 *
 * <p>Because the config port is flat path-based, the set of jails is a {@code jails} string list and each
 * jail's location lives under {@code jail.<name>.{world,x,y,z}}. A jail with no configured world resolves to
 * empty so the adapter falls back to spawn rather than teleporting into a missing world.
 */
@NullMarked
public final class ModerationSettings {

    private final ConfigStore config;
    private final List<String> jails;
    private final boolean wallClockDefault;

    public ModerationSettings(ConfigStore config) {
        this.config = Objects.requireNonNull(config, "config");
        this.jails = config.getStringList("jails", List.of()).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
        this.wallClockDefault = "wall-clock".equalsIgnoreCase(config.getString("jail-countdown", "online-only"));
    }

    /** True when {@code jail} is a configured jail name. */
    public boolean hasJail(String jail) {
        return jails.contains(jail.toLowerCase(Locale.ROOT));
    }

    /** Whether {@code jail} counts down on wall-clock time (per its own override, else the module default). */
    public boolean isWallClock(String jail) {
        String key = jail.toLowerCase(Locale.ROOT);
        String mode = config.getString("jail." + key + ".countdown", wallClockDefault ? "wall-clock" : "online-only");
        return "wall-clock".equalsIgnoreCase(mode);
    }

    /** The configured location of {@code jail}, or empty when its world is not set. */
    public Optional<JailLocation> location(String jail) {
        String key = jail.toLowerCase(Locale.ROOT);
        String world = config.getString("jail." + key + ".world", "");
        if (world.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new JailLocation(
                world,
                config.getDouble("jail." + key + ".x", 0.0),
                config.getDouble("jail." + key + ".y", 64.0),
                config.getDouble("jail." + key + ".z", 0.0)));
    }

    /** A resolved jail location: the world name and the coordinates to teleport a jailed player to. */
    public record JailLocation(String world, double x, double y, double z) {

        public JailLocation {
            Objects.requireNonNull(world, "world");
        }
    }
}
