package com.uxplima.uxmessentials.regions.adapter.inbound.command;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The production {@link RegionSelection}: it prefers a player's WorldEdit selection and falls back to two corners
 * marked with {@code /regions pos1|pos2}. WorldEdit is reached purely by reflection behind a plugin-present guard, so
 * no {@code com.sk89q} class loads on a server without it and any version mismatch degrades gracefully to the marked
 * corners rather than throwing. The marked corners are kept per player in a {@link ConcurrentHashMap} of immutable
 * {@link Corners} snapshots, mutated only through {@code compute}.
 */
@NullMarked
public final class WorldEditRegionSelection implements RegionSelection {

    private final Server server;
    private final Map<UUID, Corners> corners = new ConcurrentHashMap<>();

    public WorldEditRegionSelection(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public void mark(Player player, Corner corner) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(corner, "corner");
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        corners.compute(player.getUniqueId(), (uuid, held) -> {
            Corners current = held == null ? Corners.EMPTY : held;
            return corner == Corner.FIRST ? current.withFirst(at) : current.withSecond(at);
        });
    }

    @Override
    public Optional<RegionBounds> boundsFor(Player player) {
        Objects.requireNonNull(player, "player");
        Optional<RegionBounds> worldEdit = worldEditSelection(player);
        return worldEdit.isPresent() ? worldEdit : markedBounds(player);
    }

    /** The two-corner bounds a player marked, when both are set and share a world; else empty. */
    private Optional<RegionBounds> markedBounds(Player player) {
        Corners held = corners.get(player.getUniqueId());
        if (held == null || held.first() == null || held.second() == null) {
            return Optional.empty();
        }
        if (!held.first().world().equals(held.second().world())) {
            return Optional.empty();
        }
        return Optional.of(RegionBounds.of(held.first(), held.second()));
    }

    /**
     * The player's WorldEdit selection as bounds, or empty when WorldEdit is absent, the player has made no complete
     * selection ({@code IncompleteRegionException}), or any reflective call does not match this WorldEdit build. Every
     * failure degrades to empty so the caller falls back to the marked corners.
     */
    private Optional<RegionBounds> worldEditSelection(Player player) {
        if (server.getPluginManager().getPlugin("WorldEdit") == null) {
            return Optional.empty();
        }
        try {
            Object region = selectedRegion(player);
            if (region == null) {
                return Optional.empty();
            }
            WorldRef world = BukkitRefs.toRef(player.getWorld());
            Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);
            return Optional.of(new RegionBounds(toPosition(world, min), toPosition(world, max)));
        } catch (ReflectiveOperationException | RuntimeException noSelection) {
            return Optional.empty();
        }
    }

    /** Walk WorldEdit to the player's current selection {@code Region}, or {@code null} when none is set. */
    private @Nullable Object selectedRegion(Player player) throws ReflectiveOperationException {
        Object worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit")
                .getMethod("getInstance")
                .invoke(null);
        Object sessions = worldEdit.getClass().getMethod("getSessionManager").invoke(worldEdit);
        Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Object actor = adapter.getMethod("adapt", Player.class).invoke(null, player);
        Object session = invokeMatching(sessions, "get", actor);
        Object weWorld = adapter.getMethod("adapt", World.class).invoke(null, player.getWorld());
        return session == null ? null : invokeMatching(session, "getSelection", weWorld);
    }

    /** Invoke the single-argument method named {@code name} on {@code target} whose parameter accepts {@code arg}. */
    private static @Nullable Object invokeMatching(Object target, String name, Object arg)
            throws ReflectiveOperationException {
        for (Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(name)
                    && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(arg)) {
                return candidate.invoke(target, arg);
            }
        }
        throw new NoSuchMethodException(name);
    }

    /** Map a WorldEdit {@code BlockVector3} to a domain {@link Position} in {@code world}. */
    private static Position toPosition(WorldRef world, Object blockVector) throws ReflectiveOperationException {
        int x = (int) blockVector.getClass().getMethod("getBlockX").invoke(blockVector);
        int y = (int) blockVector.getClass().getMethod("getBlockY").invoke(blockVector);
        int z = (int) blockVector.getClass().getMethod("getBlockZ").invoke(blockVector);
        return Position.of(world, x, y, z);
    }

    /** An immutable pair of marked corners; either may be unset until the operator marks it. */
    private record Corners(
            @Nullable Position first, @Nullable Position second) {
        static final Corners EMPTY = new Corners(null, null);

        Corners withFirst(Position position) {
            return new Corners(position, second);
        }

        Corners withSecond(Position position) {
            return new Corners(first, position);
        }
    }
}
