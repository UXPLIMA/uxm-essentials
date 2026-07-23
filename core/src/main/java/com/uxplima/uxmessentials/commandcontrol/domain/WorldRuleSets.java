package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The world-scoped view of the command whitelist / blacklist: a base {@link RuleSet} plus a per-world map that overrides
 * it in named worlds. A player's command gate is decided against the rule set of the world they are currently in - a
 * world with its own override uses that override, every other world falls back to the base rule set - so an operator can
 * allow {@code /fly} in a creative world while blocking it everywhere else.
 *
 * <p>Nothing here is Bukkit-aware: the adapter reads the player's current world name and calls {@link #forWorld}, so the
 * resolution is a plain map lookup that unit-tests exactly. World names are matched case-insensitively, mirroring how
 * Bukkit treats them. When no per-world override is configured this collapses to the base rule set for every world, so
 * the common case pays nothing.
 */
public final class WorldRuleSets {

    private final RuleSet base;
    private final Map<String, RuleSet> byWorld;

    private WorldRuleSets(RuleSet base, Map<String, RuleSet> byWorld) {
        this.base = base;
        this.byWorld = byWorld;
    }

    /**
     * Build a world-scoped rule set from a base and its per-world overrides, normalising every world name to a
     * lowercase key so the current-world lookup is case-insensitive.
     *
     * @param base the rule set applied in any world without its own override
     * @param byWorld the per-world overrides, keyed by world name
     */
    public static WorldRuleSets of(RuleSet base, Map<String, RuleSet> byWorld) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(byWorld, "byWorld");
        Map<String, RuleSet> normalised = new LinkedHashMap<>();
        byWorld.forEach((world, rules) -> {
            Objects.requireNonNull(world, "world name");
            Objects.requireNonNull(rules, "rules");
            normalised.put(world.trim().toLowerCase(Locale.ROOT), rules);
        });
        return new WorldRuleSets(base, Map.copyOf(normalised));
    }

    /** A world-scoped rule set with no per-world overrides - every world resolves to {@code base}. */
    public static WorldRuleSets ofBase(RuleSet base) {
        return of(base, Map.of());
    }

    /** The rule set governing {@code worldName}: the world's own override when it has one, else the base rule set. */
    public RuleSet forWorld(@Nullable String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return base;
        }
        return byWorld.getOrDefault(worldName.trim().toLowerCase(Locale.ROOT), base);
    }

    /** True when at least one world overrides the base rule set - the signal to recompute visibility on world change. */
    public boolean hasWorldOverrides() {
        return !byWorld.isEmpty();
    }
}
