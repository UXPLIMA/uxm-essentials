package com.uxplima.uxmessentials.regions.adapter.outbound;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The WorldGuard side of the {@link RegionService}, reached purely by reflection behind a plugin-present guard — the
 * same pattern the poses {@code WorldGuardPoseFlags} and the menu vocabulary's {@code worldguard-region} condition
 * use. It walks {@code WorldGuard.getInstance().getPlatform().getRegionContainer().get(world)} to a
 * {@code RegionManager} and reads the region map, each region's priority, roster and flags off it.
 *
 * <p>The SDK is named only by string class-name ({@code com.sk89q.worldguard.WorldGuard},
 * {@code com.sk89q.worldedit.bukkit.BukkitAdapter}), so no field or method signature carries a {@code com.sk89q}
 * type: on a server without WorldGuard the {@link #available()} guard short-circuits before any {@code Class.forName},
 * so none of its classes load. Every read is fail-safe — an absent plugin, an unknown world, an unmanaged world, a
 * missing region, or any reflective failure (a version bump moving a method) reports an empty result and is logged
 * at most once, because a blank list is a better failure than a thrown command.
 *
 * <p>The mutations are declared by the port but not yet wired: {@code create}/{@code setFlag} land with the Phase 2
 * flag editor and {@code applyMemberChange}/{@code setPriority} with the Phase 3 roster editor, so they throw
 * {@link UnsupportedOperationException} for now (no Phase 1 caller reaches them).
 */
@NullMarked
public final class WorldGuardRegionService implements RegionService {

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public WorldGuardRegionService(Server server, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean available() {
        return server.getPluginManager().isPluginEnabled("WorldGuard");
    }

    @Override
    public List<RegionRef> regionsIn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        if (!available()) {
            return List.of();
        }
        try {
            Object manager = regionManager(world);
            if (manager == null) {
                return List.of();
            }
            List<RegionRef> refs = new ArrayList<>();
            for (Object id : regionsMap(manager).keySet()) {
                refs.add(new RegionRef(world, String.valueOf(id)));
            }
            return List.copyOf(refs);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
            return List.of();
        }
    }

    @Override
    public Optional<RegionRef> region(WorldRef world, String id) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        if (!available()) {
            return Optional.empty();
        }
        try {
            Object manager = regionManager(world);
            if (manager == null || !regionsMap(manager).containsKey(id)) {
                return Optional.empty();
            }
            return Optional.of(new RegionRef(world, id));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    @Override
    public List<FlagValue> flags(RegionRef region) {
        Objects.requireNonNull(region, "region");
        if (!available()) {
            return List.of();
        }
        try {
            Object protectedRegion = protectedRegion(region);
            if (protectedRegion == null) {
                return List.of();
            }
            Object result = protectedRegion.getClass().getMethod("getFlags").invoke(protectedRegion);
            List<FlagValue> flags = new ArrayList<>();
            if (result instanceof Map<?, ?> flagMap) {
                for (Map.Entry<?, ?> entry : flagMap.entrySet()) {
                    flags.add(toFlagValue(entry));
                }
            }
            return List.copyOf(flags);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
            return List.of();
        }
    }

    @Override
    public List<String> members(RegionRef region) {
        Objects.requireNonNull(region, "region");
        return domainIdentifiers(region, "getMembers");
    }

    @Override
    public List<String> owners(RegionRef region) {
        Objects.requireNonNull(region, "region");
        return domainIdentifiers(region, "getOwners");
    }

    @Override
    public int priority(RegionRef region) {
        Objects.requireNonNull(region, "region");
        if (!available()) {
            return 0;
        }
        try {
            Object protectedRegion = protectedRegion(region);
            if (protectedRegion == null) {
                return 0;
            }
            Object priority =
                    protectedRegion.getClass().getMethod("getPriority").invoke(protectedRegion);
            return priority instanceof Integer value ? value : 0;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
            return 0;
        }
    }

    @Override
    public RegionRef create(WorldRef world, String id, Position min, Position max) {
        throw new UnsupportedOperationException("region creation arrives in Phase 2");
    }

    @Override
    public void setFlag(RegionRef region, FlagValue flag) {
        throw new UnsupportedOperationException("the flag editor arrives in Phase 2");
    }

    @Override
    public void applyMemberChange(RegionMemberChange change) {
        throw new UnsupportedOperationException("the members/owners editor arrives in Phase 3");
    }

    @Override
    public void setPriority(RegionRef region, int priority) {
        throw new UnsupportedOperationException("priority editing arrives in Phase 3");
    }

    /** The member/owner identifiers of {@code region}: player names, uuids, and {@code g:}-prefixed group names. */
    private List<String> domainIdentifiers(RegionRef region, String domainGetter) {
        if (!available()) {
            return List.of();
        }
        try {
            Object protectedRegion = protectedRegion(region);
            if (protectedRegion == null) {
                return List.of();
            }
            Object domain = protectedRegion.getClass().getMethod(domainGetter).invoke(protectedRegion);
            List<String> identifiers = new ArrayList<>();
            addAll(identifiers, domain, "getPlayers", "");
            addAll(identifiers, domain, "getUniqueIds", "");
            addAll(identifiers, domain, "getGroups", "g:");
            return List.copyOf(identifiers);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
            return List.of();
        }
    }

    /** Add every element of {@code domain.<getter>()} to {@code out}, each rendered with {@code prefix}. */
    private static void addAll(List<String> out, Object domain, String getter, String prefix)
            throws ReflectiveOperationException {
        Object result = domain.getClass().getMethod(getter).invoke(domain);
        if (result instanceof Collection<?> values) {
            for (Object value : values) {
                out.add(prefix + value);
            }
        }
    }

    /** Map one live flag entry to a domain {@link FlagValue} (its registered name and stringified value). */
    private static FlagValue toFlagValue(Map.Entry<?, ?> entry) throws ReflectiveOperationException {
        Object name = entry.getKey().getClass().getMethod("getName").invoke(entry.getKey());
        Object value = entry.getValue();
        return new FlagValue(String.valueOf(name), value == null ? "" : String.valueOf(value));
    }

    /** The {@code RegionManager} for {@code world}, or {@code null} when the world is unknown or unmanaged. */
    private @Nullable Object regionManager(WorldRef world) throws ReflectiveOperationException {
        World bukkit = server.getWorld(world.uid());
        if (bukkit == null) {
            return null;
        }
        Object instance = Class.forName("com.sk89q.worldguard.WorldGuard")
                .getMethod("getInstance")
                .invoke(null);
        Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
        Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
        Object weWorld = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                .getMethod("adapt", World.class)
                .invoke(null, bukkit);
        return regionContainerGet(container, weWorld);
    }

    /** {@code RegionContainer#get(World)} — matched by the single WorldEdit-world argument; may return {@code null}. */
    private static @Nullable Object regionContainerGet(Object container, Object weWorld)
            throws ReflectiveOperationException {
        for (Method candidate : container.getClass().getMethods()) {
            if (candidate.getName().equals("get")
                    && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(weWorld)) {
                return candidate.invoke(container, weWorld);
            }
        }
        throw new NoSuchMethodException("RegionContainer.get");
    }

    /** The {@code Map<String, ProtectedRegion>} a {@code RegionManager} exposes through {@code getRegions()}. */
    private static Map<?, ?> regionsMap(Object manager) throws ReflectiveOperationException {
        Object regions = manager.getClass().getMethod("getRegions").invoke(manager);
        return regions instanceof Map<?, ?> map ? map : Map.of();
    }

    /** The live {@code ProtectedRegion} for {@code region}, or {@code null} when its world or id is gone. */
    private @Nullable Object protectedRegion(RegionRef region) throws ReflectiveOperationException {
        Object manager = regionManager(region.world());
        if (manager == null) {
            return null;
        }
        return regionsMap(manager).get(region.id());
    }

    private void degrade(Exception failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=regions_worldguard_query_failed reason={}", failure.toString());
        }
    }
}
