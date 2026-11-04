package com.uxplima.uxmessentials.guard.adapter;

import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Shape;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Each player's selection: the guard wand's two corners or polygon points, or, when WorldEdit or FAWE is installed
 * and the player has a selection there, that one. WorldEdit is reached by reflection, so the guard never needs it.
 */
public final class Selections {

    private final Server server;
    private final Map<UUID, Held> held = new ConcurrentHashMap<>();

    public Selections(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /** What a player marked with the wand. */
    private record Held(String world, @Nullable Point first, @Nullable Point second, List<Point> points) {}

    /** A marked block. */
    private record Point(int x, int y, int z) {}

    /** A shape and the world it is in. */
    public record Selection(String world, Shape shape) {}

    /** Set the first corner and start a new polygon from it. */
    public void first(Player player, int x, int y, int z) {
        held.put(player.getUniqueId(), new Held(player.getWorld().getName(), new Point(x, y, z), null,
                List.of(new Point(x, y, z))));
    }

    /** Set the second corner. */
    public void second(Player player, int x, int y, int z) {
        Held current = current(player);
        held.put(player.getUniqueId(), new Held(player.getWorld().getName(), current.first(), new Point(x, y, z),
                current.points()));
    }

    /** Add a polygon point. */
    public int point(Player player, int x, int y, int z) {
        Held current = current(player);
        List<Point> points = new ArrayList<>(current.points());
        points.add(new Point(x, y, z));
        held.put(player.getUniqueId(), new Held(player.getWorld().getName(), current.first(), current.second(),
                List.copyOf(points)));
        return points.size();
    }

    /** Forget a player's wand selection. */
    public void clear(Player player) {
        held.remove(player.getUniqueId());
    }

    /** Grow the cuboid selection; answers false when there is none. */
    public boolean expand(Player player, int west, int down, int north, int east, int up, int south) {
        Optional<Selection> selection = cuboid(player);
        if (selection.isEmpty() || !(selection.get().shape() instanceof Shape.Cuboid cuboid)) {
            return false;
        }
        Bounds grown = cuboid.box().expand(west, down, north, east, up, south);
        held.put(player.getUniqueId(), new Held(selection.get().world(),
                new Point(grown.minX(), grown.minY(), grown.minZ()),
                new Point(grown.maxX(), grown.maxY(), grown.maxZ()), List.of()));
        return true;
    }

    /** The player's cuboid: WorldEdit's selection first, then the wand corners. */
    public Optional<Selection> cuboid(Player player) {
        Optional<Selection> worldEdit = worldEdit(player);
        if (worldEdit.isPresent()) {
            return worldEdit;
        }
        Held current = held.get(player.getUniqueId());
        if (current == null || current.first() == null || current.second() == null) {
            return Optional.empty();
        }
        Point a = current.first();
        Point b = current.second();
        return Optional.of(new Selection(current.world(), new Shape.Cuboid(Bounds.of(a.x(), a.y(), a.z(), b.x(), b.y(), b.z()))));
    }

    /** The player's polygon from wand points, between the given heights. */
    public Optional<Selection> polygon(Player player, int minY, int maxY) {
        Optional<Selection> worldEdit = worldEdit(player);
        if (worldEdit.isPresent() && worldEdit.get().shape() instanceof Shape.Polygon) {
            return worldEdit;
        }
        Held current = held.get(player.getUniqueId());
        if (current == null || current.points().size() < 3) {
            return Optional.empty();
        }
        int[] xs = current.points().stream().mapToInt(Point::x).toArray();
        int[] zs = current.points().stream().mapToInt(Point::z).toArray();
        return Optional.of(new Selection(current.world(), Shape.Polygon.of(xs, zs, minY, maxY)));
    }

    /** The bounds of whatever the player has selected in WorldEdit, for the locked region check. */
    public Optional<Bounds> worldEditBounds(Player player) {
        return worldEdit(player).map(s -> s.shape().bounds());
    }

    private Held current(Player player) {
        Held current = held.get(player.getUniqueId());
        if (current == null || !current.world().equals(player.getWorld().getName())) {
            return new Held(player.getWorld().getName(), null, null, List.of());
        }
        return current;
    }

    private Optional<Selection> worldEdit(Player player) {
        if (server.getPluginManager().getPlugin("WorldEdit") == null
                && server.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            return Optional.empty();
        }
        try {
            Object region = selectedRegion(player);
            if (region == null) {
                return Optional.empty();
            }
            String world = player.getWorld().getName();
            Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);
            int[] lo = xyz(min);
            int[] hi = xyz(max);
            if (region.getClass().getSimpleName().contains("Polygonal2D")) {
                @SuppressWarnings("unchecked")
                List<Object> points = (List<Object>) region.getClass().getMethod("getPoints").invoke(region);
                int[] xs = new int[points.size()];
                int[] zs = new int[points.size()];
                for (int i = 0; i < points.size(); i++) {
                    Object point = points.get(i);
                    xs[i] = (int) point.getClass().getMethod("getBlockX").invoke(point);
                    zs[i] = (int) point.getClass().getMethod("getBlockZ").invoke(point);
                }
                if (xs.length >= 3) {
                    return Optional.of(new Selection(world, Shape.Polygon.of(xs, zs, lo[1], hi[1])));
                }
            }
            return Optional.of(new Selection(world, new Shape.Cuboid(Bounds.of(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]))));
        } catch (ReflectiveOperationException | RuntimeException noSelection) {
            return Optional.empty();
        }
    }

    private static @Nullable Object selectedRegion(Player player) throws ReflectiveOperationException {
        Object worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit").getMethod("getInstance").invoke(null);
        Object sessions = worldEdit.getClass().getMethod("getSessionManager").invoke(worldEdit);
        Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Object actor = adapter.getMethod("adapt", Player.class).invoke(null, player);
        Object session = invokeMatching(sessions, "get", actor);
        Object weWorld = adapter.getMethod("adapt", World.class).invoke(null, player.getWorld());
        return session == null ? null : invokeMatching(session, "getSelection", weWorld);
    }

    private static @Nullable Object invokeMatching(Object target, String name, Object arg)
            throws ReflectiveOperationException {
        for (Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(name) && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(arg)) {
                return candidate.invoke(target, arg);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static int[] xyz(Object vector) throws ReflectiveOperationException {
        return new int[] {
            (int) vector.getClass().getMethod("getBlockX").invoke(vector),
            (int) vector.getClass().getMethod("getBlockY").invoke(vector),
            (int) vector.getClass().getMethod("getBlockZ").invoke(vector)
        };
    }
}
