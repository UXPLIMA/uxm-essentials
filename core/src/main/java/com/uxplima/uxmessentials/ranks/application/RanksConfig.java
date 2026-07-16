package com.uxplima.uxmessentials.ranks.application;

import java.util.List;
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
 * @param prestige the prestige settings ({@code prestige.*}); {@link PrestigeSettings#enabled()} ships {@code false}
 * @param autorankEnabled whether the scheduled autorank scan is on ({@code autorank.enabled}, default {@code false})
 */
public record RanksConfig(boolean enabled, PrestigeSettings prestige, boolean autorankEnabled) {

    public RanksConfig {
        Objects.requireNonNull(prestige, "prestige");
    }

    /** Resolve the ranks config from the module's scoped {@link ConfigStore} ({@code modules.ranks}). */
    public static RanksConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new RanksConfig(
                config.getBoolean("enabled", true),
                PrestigeSettings.from(config),
                config.getBoolean("autorank.enabled", false));
    }

    /**
     * The prestige mechanic's tunables ({@code prestige.*}): the enable gate, the highest reachable level, the
     * cost charged on a prestige, the requirement and action lines run around it, and the per-level reward
     * multiplier. The requirement and action lines are operator content parsed by the same requirement/action
     * machinery a rankup uses; the numbers are validated here so a nonsensical negative never reaches the domain.
     *
     * @param enabled whether the prestige mechanic is on ({@code prestige.enabled}, default {@code false})
     * @param maxLevel the highest prestige level reachable, {@code 0} for no cap ({@code prestige.max-level})
     * @param cost the amount charged through the economy on a prestige ({@code prestige.cost}, {@code 0} for free)
     * @param requirements the raw requirement lines that gate a prestige ({@code prestige.requirements})
     * @param actions the raw action lines run after a prestige ({@code prestige.actions})
     * @param rewardMultiplier the per-level reward multiplier ({@code prestige.reward-multiplier}, default {@code 1.0})
     */
    public record PrestigeSettings(
            boolean enabled,
            int maxLevel,
            long cost,
            List<String> requirements,
            List<String> actions,
            double rewardMultiplier) {

        public PrestigeSettings {
            Objects.requireNonNull(requirements, "requirements");
            Objects.requireNonNull(actions, "actions");
            if (maxLevel < 0) {
                throw new IllegalArgumentException("prestige max-level must not be negative: " + maxLevel);
            }
            if (cost < 0) {
                throw new IllegalArgumentException("prestige cost must not be negative: " + cost);
            }
            requirements = List.copyOf(requirements);
            actions = List.copyOf(actions);
        }

        /** Resolve the prestige settings from the module's scoped {@link ConfigStore} ({@code modules.ranks}). */
        public static PrestigeSettings from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new PrestigeSettings(
                    config.getBoolean("prestige.enabled", false),
                    Math.max(0, config.getInt("prestige.max-level", 0)),
                    Math.max(0L, config.getLong("prestige.cost", 0L)),
                    config.getStringList("prestige.requirements", List.of()),
                    config.getStringList("prestige.actions", List.of()),
                    config.getDouble("prestige.reward-multiplier", 1.0));
        }
    }
}
