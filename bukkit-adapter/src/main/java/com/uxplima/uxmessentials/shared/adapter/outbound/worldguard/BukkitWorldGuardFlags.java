package com.uxplima.uxmessentials.shared.adapter.outbound.worldguard;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.WorldGuardFlags;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The WorldGuard side of the {@link WorldGuardFlags} port, reached purely by reflection behind a plugin-present guard —
 * the same pattern the poses region gate and the claim providers use. It reports whether a covering region has set a
 * named custom flag (e.g. {@code set-pwarp}, registered at load by {@link WorldGuardSetPwarpFlagRegistrar}) to DENY at
 * a location.
 *
 * <p>Named the SDK only by string class-name ({@code com.sk89q.worldguard.WorldGuard},
 * {@code com.sk89q.worldedit.bukkit.BukkitAdapter}), so no field or method signature carries a {@code com.sk89q}
 * type: on a server without WorldGuard the present-guard short-circuits before any {@code Class.forName}, so none of
 * its classes load. The gate is fail-open — an absent plugin, an unknown world, an unregistered flag, or any
 * reflective, linkage, or runtime failure (a version bump moving the query chain) all report "not denied" and are
 * logged at most once, because wrongly refusing a legitimate warp is worse than missing a rare veto.
 */
@NullMarked
public final class BukkitWorldGuardFlags implements WorldGuardFlags {

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public BukkitWorldGuardFlags(Server server, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean supported() {
        return server.getPluginManager().isPluginEnabled("WorldGuard");
    }

    @Override
    public boolean deniesFlag(String flagName, Position where) {
        Objects.requireNonNull(flagName, "flagName");
        Objects.requireNonNull(where, "where");
        if (!supported()) {
            return false;
        }
        World world = server.getWorld(where.world().uid());
        if (world == null) {
            return false;
        }
        try {
            return queryDeny(new Location(world, where.x(), where.y(), where.z()), flagName);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return false;
        }
    }

    /** True only when a covering region explicitly sets the named custom flag to DENY at {@code location}. */
    private boolean queryDeny(Location location, String flagName) throws ReflectiveOperationException {
        Object instance = Class.forName("com.sk89q.worldguard.WorldGuard")
                .getMethod("getInstance")
                .invoke(null);
        Object registry = instance.getClass().getMethod("getFlagRegistry").invoke(instance);
        Object flag = registry.getClass().getMethod("get", String.class).invoke(registry, flagName);
        if (flag == null) {
            return false; // the flag never registered (WorldGuard loaded after us) — nothing to enforce
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

    /** {@code RegionQuery#getApplicableRegions(Location)} — matched by the single WorldEdit-location argument. */
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

    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=worldguard_flag_query_failed reason={}", failure.toString());
        }
    }
}
