package com.uxplima.uxmessentials.shared.adapter.outbound.worldguard;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import org.bukkit.Location;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The reflective WorldGuard region-query chain shared by the WorldGuard adapters (the {@link WorldGuardFlags} port
 * implementation and the poses region gate). Named the SDK only by string class-name
 * ({@code com.sk89q.worldguard.WorldGuard}, {@code com.sk89q.worldedit.bukkit.BukkitAdapter}), so no field or
 * method signature carries a {@code com.sk89q} type and none of its classes load on a server without WorldGuard;
 * every caller guards on the plugin being present before invoking anything here.
 *
 * <p>{@link #queryDeny} rethrows its reflective failure so each caller can apply its own fail-open policy and its
 * own catch set (the poses gate treats a reflective or runtime failure as "not denied"; the port implementation
 * also folds in {@code LinkageError}).
 */
@NullMarked
public final class WorldGuardReflection {

    private WorldGuardReflection() {}

    /** True only when a covering region explicitly sets the named custom flag to DENY at {@code location}. */
    public static boolean queryDeny(Location location, String flagName) throws ReflectiveOperationException {
        Object instance = Class.forName("com.sk89q.worldguard.WorldGuard")
                .getMethod("getInstance")
                .invoke(null);
        Object registry = instance.getClass().getMethod("getFlagRegistry").invoke(instance);
        Object flag = registry.getClass().getMethod("get", String.class).invoke(registry, flagName);
        if (flag == null) {
            return false; // the flag never registered (WorldGuard loaded after us); nothing to enforce
        }
        Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
        Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
        Object query = container.getClass().getMethod("createQuery").invoke(container);
        Object regions = getApplicableRegions(query, adapt(location));
        return regions != null && isDeny(queryState(regions, flag));
    }

    /** Adapt a Bukkit {@link Location} to the WorldEdit location the region container's query expects. */
    private static Object adapt(Location location) throws ReflectiveOperationException {
        return Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                .getMethod("adapt", Location.class)
                .invoke(null, location);
    }

    /** {@code RegionQuery#getApplicableRegions(Location)} matched by the single WorldEdit-location argument. */
    private static @Nullable Object getApplicableRegions(Object query, Object weLocation)
            throws ReflectiveOperationException {
        for (Method candidate : query.getClass().getMethods()) {
            if (candidate.getName().equals("getApplicableRegions")
                    && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(weLocation)) {
                return candidate.invoke(query, weLocation);
            }
        }
        throw new NoSuchMethodException("getApplicableRegions");
    }

    /** {@code ApplicableRegionSet#queryState(RegionAssociable, StateFlag...)} with a null subject and our one flag. */
    private static @Nullable Object queryState(Object regions, Object flag) throws ReflectiveOperationException {
        Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
        Object flags = Array.newInstance(stateFlag, 1);
        Array.set(flags, 0, flag);
        for (Method candidate : regions.getClass().getMethods()) {
            if (candidate.getName().equals("queryState")
                    && candidate.getParameterCount() == 2
                    && candidate.getParameterTypes()[1].isArray()) {
                return candidate.invoke(regions, null, flags);
            }
        }
        throw new NoSuchMethodException("queryState");
    }

    /** The resolved {@code StateFlag.State} is a DENY only when its enum constant is named {@code DENY}. */
    static boolean isDeny(@Nullable Object state) {
        return state instanceof Enum<?> value && "DENY".equals(value.name());
    }
}
