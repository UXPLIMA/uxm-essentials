package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The world-scoped view of the plugin-hide feature: a base {@link HidePolicy} plus a per-world map that overrides it in
 * named worlds, so an operator can hide the plugin-listing commands in a public survival world while revealing them in a
 * staff world. It mirrors {@link WorldRuleSets}: the policy of the player's current world governs the scrub, a world
 * with its own override uses that override, every other world falls back to the base policy.
 *
 * <p>Nothing here is Bukkit-aware: the adapter reads the player's current world name and calls {@link #forWorld}. World
 * names are matched case-insensitively, and with no per-world override this collapses to the base policy for every
 * world.
 */
public final class WorldHidePolicies {

    private final HidePolicy base;
    private final Map<String, HidePolicy> byWorld;

    private WorldHidePolicies(HidePolicy base, Map<String, HidePolicy> byWorld) {
        this.base = base;
        this.byWorld = byWorld;
    }

    /**
     * Build a world-scoped hide policy from a base and its per-world overrides, normalising every world name to a
     * lowercase key so the current-world lookup is case-insensitive.
     *
     * @param base the hide policy applied in any world without its own override
     * @param byWorld the per-world overrides, keyed by world name
     */
    public static WorldHidePolicies of(HidePolicy base, Map<String, HidePolicy> byWorld) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(byWorld, "byWorld");
        Map<String, HidePolicy> normalised = new LinkedHashMap<>();
        byWorld.forEach((world, policy) -> {
            Objects.requireNonNull(world, "world name");
            Objects.requireNonNull(policy, "policy");
            normalised.put(world.trim().toLowerCase(Locale.ROOT), policy);
        });
        return new WorldHidePolicies(base, Map.copyOf(normalised));
    }

    /** A world-scoped hide policy with no per-world overrides - every world resolves to {@code base}. */
    public static WorldHidePolicies ofBase(HidePolicy base) {
        return of(base, Map.of());
    }

    /** The hide policy governing {@code worldName}: the world's own override when it has one, else the base policy. */
    public HidePolicy forWorld(@Nullable String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return base;
        }
        return byWorld.getOrDefault(worldName.trim().toLowerCase(Locale.ROOT), base);
    }

    /** True when at least one world overrides the base hide policy - the signal to recompute visibility on world change. */
    public boolean hasWorldOverrides() {
        return !byWorld.isEmpty();
    }
}
