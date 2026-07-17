package com.uxplima.uxmessentials.invrollback.application;

import java.util.Objects;

import com.uxplima.uxmessentials.invrollback.domain.RetentionPolicy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/invrollback/config.conf}: the enable gate, the two capture triggers
 * (death and logout), whether the ender chest rides along, and the retention limits. Resolved once from the
 * module's scoped {@link ConfigStore} when the module starts and, per the atomic-reload rule, swapped whole on
 * reload, so a capture mid-reload sees one coherent snapshot of the settings.
 *
 * <p>The HOCON keys are kebab-case ({@code capture.on-death}, {@code include-enderchest}, {@code
 * retention.max-per-player}); every knob carries the default the bundled config ships, so an operator who deletes
 * a line falls back to the shipped value.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param captureOnDeath snapshot on death ({@code capture.on-death}, default {@code true})
 * @param captureOnLogout snapshot on logout ({@code capture.on-logout}, default {@code true})
 * @param includeEnderchest store the ender chest alongside the inventory ({@code include-enderchest}, default true)
 * @param maxPerPlayer the per-player snapshot cap ({@code retention.max-per-player}, default 10; {@code 0} = off)
 * @param maxAgeDays the greatest snapshot age in days ({@code retention.max-age-days}, default 30; {@code 0} = off)
 */
public record InvrollbackConfig(
        boolean enabled,
        boolean captureOnDeath,
        boolean captureOnLogout,
        boolean includeEnderchest,
        int maxPerPlayer,
        int maxAgeDays) {

    private static final int DEFAULT_MAX_PER_PLAYER = 10;
    private static final int DEFAULT_MAX_AGE_DAYS = 30;

    /** Resolve the invrollback config from the module's scoped {@link ConfigStore} ({@code modules.invrollback}). */
    public static InvrollbackConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new InvrollbackConfig(
                config.getBoolean("enabled", true),
                config.getBoolean("capture.on-death", true),
                config.getBoolean("capture.on-logout", true),
                config.getBoolean("include-enderchest", true),
                config.getInt("retention.max-per-player", DEFAULT_MAX_PER_PLAYER),
                config.getInt("retention.max-age-days", DEFAULT_MAX_AGE_DAYS));
    }

    /** The pure prune rule these limits describe, for the scheduled retention sweep. */
    public RetentionPolicy retentionPolicy() {
        return new RetentionPolicy(maxPerPlayer, maxAgeDays);
    }
}
