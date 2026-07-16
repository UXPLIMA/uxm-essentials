package com.uxplima.uxmessentials.ranks.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/ranks/config.conf}: the module enable gate plus the two
 * feature switches — prestige and autorank — later phases (P3, P4) gate their behaviour on. The ladder itself
 * lives in a sibling {@code ranks.conf} and is read by {@link RankLadders}, not here, so this record holds only
 * the module-level tunables. It is resolved once from the module's scoped {@link ConfigStore} on start and, per
 * the atomic-reload rule, swapped whole on reload — so a reader always sees one coherent snapshot.
 *
 * <p>Prestige and autorank ship {@code false}: both are opt-in progression mechanics an operator turns on once
 * their ladder is authored, so a fresh install has plain rankup only. An operator who deletes a line falls back
 * to the shipped default rather than to an accidental {@code true}.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param prestigeEnabled whether the prestige mechanic is on ({@code prestige.enabled}, default {@code false})
 * @param autorankEnabled whether the scheduled autorank scan is on ({@code autorank.enabled}, default {@code false})
 */
public record RanksConfig(boolean enabled, boolean prestigeEnabled, boolean autorankEnabled) {

    /** Resolve the ranks config from the module's scoped {@link ConfigStore} ({@code modules.ranks}). */
    public static RanksConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new RanksConfig(
                config.getBoolean("enabled", true),
                config.getBoolean("prestige.enabled", false),
                config.getBoolean("autorank.enabled", false));
    }
}
