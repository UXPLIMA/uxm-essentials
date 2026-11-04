package com.uxplima.uxmessentials.guard.adapter.outbound;

import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Draws region borders for one viewer (E03). In {@code lines} mode each edge is a thin, glowing block display that
 * only the viewer is shown; in {@code particles} mode the edges are traced with dust particles sent to the viewer
 * alone. Either way nobody else sees anything, and everything goes away after the configured time or on hide.
 */
public final class RegionDisplay {

    private static final float THICKNESS = 0.06f;
    private static final int ROUND_SEGMENTS = 32;

    private final Plugin plugin;
    private final Map<UUID, List<BlockDisplay>> shown = new ConcurrentHashMap<>();

    public RegionDisplay(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** One straight edge between two points. */
    private record Edge(double x1, double y1, double z1, double x2, double y2, double z2) {}

    /** Draw these shapes for {@code viewer}; replaces what was drawn before. Run on the viewer's thread. */
    public void show(Player viewer, World world, List<Shape> shapes, String mode, int range, Color color) {
        hide(viewer);
        Location eye = viewer.getLocation();
        List<Edge> edges = new ArrayList<>();
        for (Shape shape : shapes) {
            edges.addAll(edges(shape, eye == null ? 64 : eye.getBlockY()));
        }
        if (eye != null) {
            double maxSq = (double) range * range * 4;
            edges.removeIf(e -> eye.distanceSquared(new Location(world, (e.x1 + e.x2) / 2, (e.y1 + e.y2) / 2,
                    (e.z1 + e.z2) / 2)) > maxSq);
        }
        if (mode.equals("particles")) {
            particles(viewer, world, edges, color);
            return;
        }
        List<BlockDisplay> entities = new ArrayList<>();
        if (eye == null) {
            return;
        }
        // Every display stands where the viewer stands and is moved to its edge by its transformation, so on Folia
        // they are all spawned in the region the viewer's thread owns.
        Location anchor = new Location(world, eye.getBlockX(), eye.getBlockY(), eye.getBlockZ());
        for (Edge edge : edges) {
            BlockDisplay display = world.spawn(anchor, BlockDisplay.class, d -> {
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
                d.setGlowing(true);
                d.setGlowColorOverride(color);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(4f);
                d.setTransformation(transformation(edge, anchor));
            });
            viewer.showEntity(plugin, display);
            entities.add(display);
        }
        shown.put(viewer.getUniqueId(), entities);
    }

    /** Draw the particle edges again; called every second while a particle display lasts. */
    public void particles(Player viewer, World world, List<Shape> shapes, int range, Color color) {
        Location eye = viewer.getLocation();
        List<Edge> edges = new ArrayList<>();
        for (Shape shape : shapes) {
            edges.addAll(edges(shape, eye == null ? 64 : eye.getBlockY()));
        }
        particles(viewer, world, edges, color);
    }

    /** Remove what was drawn for {@code viewer}. */
    public void hide(Player viewer) {
        List<BlockDisplay> entities = shown.remove(viewer.getUniqueId());
        if (entities != null) {
            for (BlockDisplay display : entities) {
                display.getScheduler().run(plugin, task -> display.remove(), null);
            }
        }
    }

    /** True when something is drawn for {@code viewer}. */
    public boolean showing(Player viewer) {
        return shown.containsKey(viewer.getUniqueId());
    }

    /** Remove everything, for a disable. */
    public void clear() {
        shown.values().forEach(list -> list.forEach(d -> d.getScheduler().run(plugin, task -> d.remove(), null)));
        shown.clear();
    }

    private static void particles(Player viewer, World world, List<Edge> edges, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);
        for (Edge edge : edges) {
            double length = Math.sqrt(sq(edge.x2 - edge.x1) + sq(edge.y2 - edge.y1) + sq(edge.z2 - edge.z1));
            int steps = (int) Math.min(256, Math.max(1, Math.ceil(length)));
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                viewer.spawnParticle(Particle.DUST, new Location(world, edge.x1 + (edge.x2 - edge.x1) * t,
                        edge.y1 + (edge.y2 - edge.y1) * t, edge.z1 + (edge.z2 - edge.z1) * t), 1, dust);
            }
        }
    }

    private static Transformation transformation(Edge edge, Location anchor) {
        Vector3f direction = new Vector3f((float) (edge.x2 - edge.x1), (float) (edge.y2 - edge.y1),
                (float) (edge.z2 - edge.z1));
        float length = direction.length();
        Vector3f x = new Vector3f(1, 0, 0);
        AxisAngle4f none = new AxisAngle4f();
        AxisAngle4f rotation;
        if (length == 0) {
            rotation = none;
        } else {
            Vector3f unit = new Vector3f(direction).normalize();
            Vector3f axis = new Vector3f(x).cross(unit);
            float angle = (float) Math.acos(Math.max(-1, Math.min(1, x.dot(unit))));
            rotation = axis.lengthSquared() < 1e-6f ? new AxisAngle4f(angle, 0, 1, 0)
                    : new AxisAngle4f(angle, axis.normalize());
        }
        Vector3f offset = new Vector3f((float) (edge.x1 - anchor.getX()), (float) (edge.y1 - anchor.getY()),
                (float) (edge.z1 - anchor.getZ()));
        return new Transformation(offset, rotation,
                new Vector3f(Math.max(length, THICKNESS), THICKNESS, THICKNESS), new AxisAngle4f());
    }

    private static List<Edge> edges(Shape shape, int eyeY) {
        List<Edge> out = new ArrayList<>();
        switch (shape) {
            case Shape.Cuboid c -> box(out, c.box());
            case Shape.Polygon p -> {
                int[] xs = p.xs();
                int[] zs = p.zs();
                double low = p.minY();
                double high = p.maxY() + 1.0;
                for (int i = 0; i < xs.length; i++) {
                    int j = (i + 1) % xs.length;
                    out.add(new Edge(xs[i] + 0.5, low, zs[i] + 0.5, xs[j] + 0.5, low, zs[j] + 0.5));
                    out.add(new Edge(xs[i] + 0.5, high, zs[i] + 0.5, xs[j] + 0.5, high, zs[j] + 0.5));
                    out.add(new Edge(xs[i] + 0.5, low, zs[i] + 0.5, xs[i] + 0.5, high, zs[i] + 0.5));
                }
            }
            case Shape.Cylinder c -> ring(out, c.centerX() + 0.5, c.centerZ() + 0.5, c.radiusX() + 0.5,
                    c.radiusZ() + 0.5, c.minY(), c.maxY() + 1.0);
            case Shape.Sphere s -> {
                double r = s.radius() + 0.5;
                ring(out, s.centerX() + 0.5, s.centerZ() + 0.5, r, r, s.centerY() + 0.5, s.centerY() + 0.5);
                for (int i = 0; i < ROUND_SEGMENTS; i++) {
                    double a1 = Math.PI * 2 * i / ROUND_SEGMENTS;
                    double a2 = Math.PI * 2 * (i + 1) / ROUND_SEGMENTS;
                    out.add(new Edge(s.centerX() + 0.5 + Math.cos(a1) * r, s.centerY() + 0.5 + Math.sin(a1) * r,
                            s.centerZ() + 0.5, s.centerX() + 0.5 + Math.cos(a2) * r,
                            s.centerY() + 0.5 + Math.sin(a2) * r, s.centerZ() + 0.5));
                    out.add(new Edge(s.centerX() + 0.5, s.centerY() + 0.5 + Math.sin(a1) * r,
                            s.centerZ() + 0.5 + Math.cos(a1) * r, s.centerX() + 0.5,
                            s.centerY() + 0.5 + Math.sin(a2) * r, s.centerZ() + 0.5 + Math.cos(a2) * r));
                }
            }
            case Shape.ChunkSet c -> {
                for (long key : c.chunks()) {
                    int cx = Shape.chunkX(key);
                    int cz = Shape.chunkZ(key);
                    double y = Math.max(c.minY(), Math.min(c.maxY(), eyeY));
                    if (!c.chunks().contains(Shape.chunkKey(cx, cz - 1))) {
                        out.add(new Edge(cx << 4, y, cz << 4, (cx << 4) + 16, y, cz << 4));
                    }
                    if (!c.chunks().contains(Shape.chunkKey(cx, cz + 1))) {
                        out.add(new Edge(cx << 4, y, (cz << 4) + 16, (cx << 4) + 16, y, (cz << 4) + 16));
                    }
                    if (!c.chunks().contains(Shape.chunkKey(cx - 1, cz))) {
                        out.add(new Edge(cx << 4, y, cz << 4, cx << 4, y, (cz << 4) + 16));
                    }
                    if (!c.chunks().contains(Shape.chunkKey(cx + 1, cz))) {
                        out.add(new Edge((cx << 4) + 16, y, cz << 4, (cx << 4) + 16, y, (cz << 4) + 16));
                    }
                }
            }
            case Shape.Global g -> {
                // The whole world has no border to draw.
            }
        }
        return out;
    }

    private static void box(List<Edge> out, Bounds b) {
        double x1 = b.minX();
        double y1 = b.minY();
        double z1 = b.minZ();
        double x2 = b.maxX() + 1.0;
        double y2 = b.maxY() + 1.0;
        double z2 = b.maxZ() + 1.0;
        out.add(new Edge(x1, y1, z1, x2, y1, z1));
        out.add(new Edge(x1, y1, z2, x2, y1, z2));
        out.add(new Edge(x1, y2, z1, x2, y2, z1));
        out.add(new Edge(x1, y2, z2, x2, y2, z2));
        out.add(new Edge(x1, y1, z1, x1, y1, z2));
        out.add(new Edge(x2, y1, z1, x2, y1, z2));
        out.add(new Edge(x1, y2, z1, x1, y2, z2));
        out.add(new Edge(x2, y2, z1, x2, y2, z2));
        out.add(new Edge(x1, y1, z1, x1, y2, z1));
        out.add(new Edge(x2, y1, z1, x2, y2, z1));
        out.add(new Edge(x1, y1, z2, x1, y2, z2));
        out.add(new Edge(x2, y1, z2, x2, y2, z2));
    }

    private static void ring(List<Edge> out, double cx, double cz, double rx, double rz, double low, double high) {
        for (int i = 0; i < ROUND_SEGMENTS; i++) {
            double a1 = Math.PI * 2 * i / ROUND_SEGMENTS;
            double a2 = Math.PI * 2 * (i + 1) / ROUND_SEGMENTS;
            double x1 = cx + Math.cos(a1) * rx;
            double z1 = cz + Math.sin(a1) * rz;
            double x2 = cx + Math.cos(a2) * rx;
            double z2 = cz + Math.sin(a2) * rz;
            out.add(new Edge(x1, low, z1, x2, low, z2));
            if (high != low) {
                out.add(new Edge(x1, high, z1, x2, high, z2));
                if (i % 4 == 0) {
                    out.add(new Edge(x1, low, z1, x1, high, z1));
                }
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
