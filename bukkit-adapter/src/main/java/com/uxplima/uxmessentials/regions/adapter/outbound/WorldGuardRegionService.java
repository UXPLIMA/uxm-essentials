package com.uxplima.uxmessentials.regions.adapter.outbound;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagDescriptor;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.regions.domain.RegionServiceException;
import com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardReflection;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The WorldGuard side of the {@link RegionService}, reached purely by reflection behind a plugin-present guard: the
 * same pattern the poses {@code WorldGuardPoseFlags} and the menu vocabulary's {@code worldguard-region} condition
 * use. It walks {@code WorldGuard.getInstance().getPlatform().getRegionContainer().get(world)} to a
 * {@code RegionManager} and reads the region map, each region's priority, roster and flags off it.
 *
 * <p>The SDK is named only by string class-name ({@code com.sk89q.worldguard.WorldGuard},
 * {@code com.sk89q.worldedit.bukkit.BukkitAdapter}) through the shared {@link WorldGuardReflection} entry point,
 * so no field or method signature carries a {@code com.sk89q}
 * type: on a server without WorldGuard the {@link #available()} guard short-circuits before any {@code Class.forName},
 * so none of its classes load. Every read is fail-safe: an absent plugin, an unknown world, an unmanaged world, a
 * missing region, or any reflective failure (a version bump moving a method) reports an empty result and is logged
 * at most once, because a blank list is a better failure than a thrown command.
 *
 * <p>The mutations: {@code create}, {@code setFlag}, {@code applyMemberChange} and {@code setPriority}: must, unlike
 * the fail-safe reads, not fail silently: a reflective failure or a rejected request is wrapped in a
 * {@link RegionServiceException} the command surface turns into an operator-facing error rather than a swallowed
 * no-op. Each mutates the live region in memory and leans on WorldGuard's own background/shutdown save to persist
 * (a roster change dirties the region through its domain, {@code setPriority} dirties the region directly), so none
 * blocks the tick thread on a disk write.
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
        return WorldGuardReflection.isEnabled(server);
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
    public List<FlagDescriptor> flagDescriptors(RegionRef region) {
        Objects.requireNonNull(region, "region");
        if (!available()) {
            return List.of();
        }
        try {
            List<?> allFlags = registeredFlags();
            Map<String, Object> current = setValuesByName(region);
            List<FlagDescriptor> descriptors = new ArrayList<>();
            for (Object flag : allFlags) {
                String name = WorldGuardFlagTypes.flagName(flag);
                descriptors.add(WorldGuardFlagTypes.describe(flag, current.get(name)));
            }
            descriptors.sort(Comparator.comparing(FlagDescriptor::name));
            return List.copyOf(descriptors);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            degrade(failure);
            return List.of();
        }
    }

    /** The region's currently-set flag values keyed by flag name, for pairing with the full registry list. */
    private Map<String, Object> setValuesByName(RegionRef region) throws ReflectiveOperationException {
        Object protectedRegion = protectedRegion(region);
        if (protectedRegion == null) {
            return Map.of();
        }
        Object result = protectedRegion.getClass().getMethod("getFlags").invoke(protectedRegion);
        Map<String, Object> byName = new HashMap<>();
        if (result instanceof Map<?, ?> flagMap) {
            for (Map.Entry<?, ?> entry : flagMap.entrySet()) {
                byName.put(WorldGuardFlagTypes.flagName(entry.getKey()), entry.getValue());
            }
        }
        return byName;
    }

    /** Every flag WorldGuard has registered ({@code FlagRegistry#getAll}), across every type. */
    private List<?> registeredFlags() throws ReflectiveOperationException {
        Object registry = flagRegistry();
        Object all = registry.getClass().getMethod("getAll").invoke(registry);
        return all instanceof List<?> flags ? flags : List.of();
    }

    /** {@code WorldGuard.getInstance().getFlagRegistry()}: the registry both reads and writes look flags up in. */
    private static Object flagRegistry() throws ReflectiveOperationException {
        Object instance = WorldGuardReflection.instance();
        return instance.getClass().getMethod("getFlagRegistry").invoke(instance);
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
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (!available()) {
            throw new UnsupportedOperationException("WorldGuard is not installed");
        }
        try {
            Object manager = regionManager(world);
            if (manager == null) {
                throw new RegionServiceException("no WorldGuard region manager for world " + world.name());
            }
            Object region = newCuboidRegion(id, min, max);
            addRegion(manager, region);
            // The manager is now dirty; WorldGuard's periodic background saver and its shutdown save persist it, so
            // create stays a fast in-memory call rather than a blocking disk write on the tick thread.
            return new RegionRef(world, id);
        } catch (ReflectiveOperationException failure) {
            throw new RegionServiceException("could not create region " + id, failure);
        }
    }

    @Override
    public void setFlag(RegionRef region, FlagValue flag) {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(flag, "flag");
        if (!available()) {
            throw new UnsupportedOperationException("WorldGuard is not installed");
        }
        try {
            Object protectedRegion = protectedRegion(region);
            if (protectedRegion == null) {
                throw new RegionServiceException("no region " + region.id() + " to flag");
            }
            Object wgFlag = flagByName(flag.name());
            if (wgFlag == null) {
                throw new RegionServiceException("unknown flag: " + flag.name());
            }
            // A blank value clears the flag (unset); otherwise the portable value is converted to the flag's own type.
            Object value = WorldGuardFlagTypes.toWgValue(wgFlag, flag.value());
            setRegionFlag(protectedRegion, wgFlag, value);
        } catch (ReflectiveOperationException failure) {
            throw new RegionServiceException("could not set flag " + flag.name(), failure);
        } catch (IllegalArgumentException invalid) {
            throw new RegionServiceException("invalid value for flag " + flag.name() + ": " + flag.value(), invalid);
        }
    }

    /** The registered flag named {@code name}, of any type, or {@code null} when the registry has none. */
    private static @Nullable Object flagByName(String name) throws ReflectiveOperationException {
        Object registry = flagRegistry();
        return registry.getClass().getMethod("get", String.class).invoke(registry, name);
    }

    @Override
    public void applyMemberChange(RegionMemberChange change) {
        Objects.requireNonNull(change, "change");
        if (!available()) {
            throw new UnsupportedOperationException("WorldGuard is not installed");
        }
        try {
            Object protectedRegion = protectedRegion(change.region());
            if (protectedRegion == null) {
                throw new RegionServiceException("no region " + change.region().id() + " to edit");
            }
            Object domain = domainOf(protectedRegion, change.role());
            applyToDomain(domain, change.player(), change.action());
            // Mutating the domain marks it: and so the region: dirty; WorldGuard's periodic and shutdown saves
            // persist it, keeping this a fast in-memory call rather than a blocking disk write on the tick thread.
        } catch (ReflectiveOperationException failure) {
            throw new RegionServiceException(
                    "could not change roster of " + change.region().id(), failure);
        }
    }

    @Override
    public void setPriority(RegionRef region, int priority) {
        Objects.requireNonNull(region, "region");
        if (!available()) {
            throw new UnsupportedOperationException("WorldGuard is not installed");
        }
        try {
            Object protectedRegion = protectedRegion(region);
            if (protectedRegion == null) {
                throw new RegionServiceException("no region " + region.id() + " to prioritise");
            }
            // ProtectedRegion.setPriority dirties the region itself, so the background save picks it up.
            protectedRegion.getClass().getMethod("setPriority", int.class).invoke(protectedRegion, priority);
        } catch (ReflectiveOperationException failure) {
            throw new RegionServiceException("could not set priority of " + region.id(), failure);
        }
    }

    /** The region's owner or member {@code DefaultDomain}, selected by the change's role. */
    private static Object domainOf(Object protectedRegion, RegionMemberChange.Role role)
            throws ReflectiveOperationException {
        String getter = role == RegionMemberChange.Role.OWNER ? "getOwners" : "getMembers";
        return protectedRegion.getClass().getMethod(getter).invoke(protectedRegion);
    }

    /** {@code DefaultDomain.addPlayer(UUID)} / {@code removePlayer(UUID)}: the uuid overload, chosen by parameter. */
    private static void applyToDomain(Object domain, UUID player, RegionMemberChange.Action action)
            throws ReflectiveOperationException {
        String method = action == RegionMemberChange.Action.ADD ? "addPlayer" : "removePlayer";
        domain.getClass().getMethod(method, UUID.class).invoke(domain, player);
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
        return regionContainerGet(WorldGuardReflection.regionContainer(), WorldGuardReflection.adaptWorld(bukkit));
    }

    /** {@code RegionContainer#get(World)}: matched by the single WorldEdit-world argument; may return {@code null}. */
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

    /** A new {@code ProtectedCuboidRegion(id, min, max)} spanning the two positions' block cells. */
    private static Object newCuboidRegion(String id, Position min, Position max) throws ReflectiveOperationException {
        Object minVector = blockVector(min);
        Object maxVector = blockVector(max);
        Class<?> vectorClass = minVector.getClass();
        return Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion")
                .getConstructor(String.class, vectorClass, vectorClass)
                .newInstance(id, minVector, maxVector);
    }

    /** {@code BlockVector3.at(blockX, blockY, blockZ)} for a domain {@link Position}. */
    private static Object blockVector(Position position) throws ReflectiveOperationException {
        return Class.forName("com.sk89q.worldedit.math.BlockVector3")
                .getMethod("at", int.class, int.class, int.class)
                .invoke(null, position.blockX(), position.blockY(), position.blockZ());
    }

    /** {@code RegionManager.addRegion(ProtectedRegion)}: matched by the single {@code ProtectedRegion} argument. */
    private static void addRegion(Object manager, Object region) throws ReflectiveOperationException {
        Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
        manager.getClass().getMethod("addRegion", protectedRegionClass).invoke(manager, region);
    }

    /** {@code ProtectedRegion.setFlag(Flag, value)}: a null value clears the flag; matched by its two arguments. */
    private static void setRegionFlag(Object region, Object flag, @Nullable Object value)
            throws ReflectiveOperationException {
        for (Method candidate : region.getClass().getMethods()) {
            if (candidate.getName().equals("setFlag") && candidate.getParameterCount() == 2) {
                candidate.invoke(region, flag, value);
                return;
            }
        }
        throw new NoSuchMethodException("ProtectedRegion.setFlag");
    }

    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=regions_worldguard_query_failed reason={}", failure.toString());
        }
    }
}
