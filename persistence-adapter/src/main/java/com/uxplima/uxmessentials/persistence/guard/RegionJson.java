package com.uxplima.uxmessentials.persistence.guard;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.Target;
import org.jspecify.annotations.Nullable;

/**
 * Regions to and from JSON documents. The same form is stored in {@code guard_regions.data}, in history rows and
 * in backup files, so a backup can be read back by an import undo on any server.
 *
 * <p>Reading is strict about structure and lenient about content: an unknown behaviour key or a rule for an
 * action this build does not know is kept out rather than failing the whole region.
 */
public final class RegionJson {

    private RegionJson() {}

    /** One region as a JSON object. */
    public static JsonObject toJson(Region region) {
        JsonObject o = new JsonObject();
        o.addProperty("scope", region.scope().value());
        o.addProperty("world", region.world());
        o.addProperty("id", region.id().value());
        o.add("shape", shape(region.shape()));
        o.addProperty("priority", region.priority());
        if (region.parent() != null) {
            o.addProperty("parent", region.parent().value());
        }
        if (region.family() != null) {
            o.addProperty("family", region.family());
        }
        if (region.template() != null) {
            o.addProperty("template", region.template());
        }
        JsonArray tags = new JsonArray();
        region.tags().forEach(tags::add);
        o.add("tags", tags);
        o.addProperty("locked", region.locked());
        JsonArray rules = new JsonArray();
        for (Rule rule : region.rules()) {
            JsonObject r = new JsonObject();
            r.addProperty("action", rule.action().value());
            r.addProperty("target", rule.target().text());
            r.addProperty("effect", rule.effect().word());
            rules.add(r);
        }
        o.add("rules", rules);
        JsonObject behaviours = new JsonObject();
        region.behaviours().asMap().forEach((key, value) -> behaviours.addProperty(key.key(), value));
        o.add("behaviours", behaviours);
        JsonObject roles = new JsonObject();
        region.roles().forEach((player, role) -> roles.addProperty(player.toString(), role.value()));
        o.add("roles", roles);
        if (region.importBatch() != null) {
            o.addProperty("importBatch", region.importBatch());
        }
        return o;
    }

    /** One region as a JSON string. */
    public static String write(Region region) {
        return toJson(region).toString();
    }

    /** Read a region from a JSON string. */
    public static Region read(String json) {
        return fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    /** Read a region from a JSON object. */
    public static Region fromJson(JsonObject o) {
        RegionKey key = new RegionKey(
                o.get("world").getAsString(), RegionId.of(o.get("id").getAsString()));
        List<Rule> rules = new ArrayList<>();
        for (JsonElement element : array(o, "rules")) {
            JsonObject r = element.getAsJsonObject();
            try {
                Target target = Target.parse(r.get("target").getAsString()).orElse(null);
                Effect effect = Effect.parse(r.get("effect").getAsString()).orElse(null);
                if (target != null && effect != null) {
                    rules.add(new Rule(ActionId.of(r.get("action").getAsString()), target, effect));
                }
            } catch (IllegalArgumentException skipped) {
                // A rule this build cannot read is left out rather than failing the region.
            }
        }
        Map<BehaviourKey, String> behaviours = new EnumMap<>(BehaviourKey.class);
        JsonObject b = object(o, "behaviours");
        for (String name : b.keySet()) {
            BehaviourKey.parse(name)
                    .ifPresent(k -> behaviours.put(k, b.get(name).getAsString()));
        }
        Map<UUID, RoleName> roles = new HashMap<>();
        JsonObject r = object(o, "roles");
        for (String player : r.keySet()) {
            try {
                roles.put(UUID.fromString(player), RoleName.of(r.get(player).getAsString()));
            } catch (IllegalArgumentException skipped) {
                // A malformed entry is dropped; the rest of the roster stays.
            }
        }
        Set<String> tags = new HashSet<>();
        for (JsonElement tag : array(o, "tags")) {
            tags.add(tag.getAsString());
        }
        return new Region(
                key,
                new GuardScope(o.get("scope").getAsString()),
                shape(o.getAsJsonObject("shape")),
                o.has("priority") ? o.get("priority").getAsInt() : 0,
                o.has("parent") ? RegionId.of(o.get("parent").getAsString()) : null,
                string(o, "family"),
                string(o, "template"),
                tags,
                o.has("locked") && o.get("locked").getAsBoolean(),
                rules,
                Behaviours.of(behaviours),
                roles,
                string(o, "importBatch"));
    }

    /** A list of regions as a JSON array string, for backups. */
    public static String writeAll(List<Region> regions) {
        JsonArray array = new JsonArray();
        regions.forEach(region -> array.add(toJson(region)));
        return array.toString();
    }

    /** Read a JSON array of regions. */
    public static List<Region> readAll(String json) {
        List<Region> out = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            out.add(fromJson(element.getAsJsonObject()));
        }
        return out;
    }

    private static JsonObject shape(Shape shape) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", shape.kind());
        switch (shape) {
            case Shape.Cuboid c -> {
                Bounds b = c.box();
                o.add("min", ints(b.minX(), b.minY(), b.minZ()));
                o.add("max", ints(b.maxX(), b.maxY(), b.maxZ()));
            }
            case Shape.Polygon p -> {
                o.add("xs", ints(p.xs()));
                o.add("zs", ints(p.zs()));
                o.addProperty("minY", p.minY());
                o.addProperty("maxY", p.maxY());
            }
            case Shape.Cylinder c -> {
                o.addProperty("cx", c.centerX());
                o.addProperty("cz", c.centerZ());
                o.addProperty("rx", c.radiusX());
                o.addProperty("rz", c.radiusZ());
                o.addProperty("minY", c.minY());
                o.addProperty("maxY", c.maxY());
            }
            case Shape.Sphere s -> {
                o.add("center", ints(s.centerX(), s.centerY(), s.centerZ()));
                o.addProperty("radius", s.radius());
            }
            case Shape.ChunkSet c -> {
                JsonArray chunks = new JsonArray();
                for (long key : c.chunks()) {
                    chunks.add(ints(Shape.chunkX(key), Shape.chunkZ(key)));
                }
                o.add("chunks", chunks);
                o.addProperty("minY", c.minY());
                o.addProperty("maxY", c.maxY());
            }
            case Shape.Global g -> {
                // The whole world: the kind says everything.
            }
        }
        return o;
    }

    private static Shape shape(JsonObject o) {
        String kind = o.get("kind").getAsString();
        return switch (kind) {
            case "cuboid" -> {
                int[] min = ints(o.getAsJsonArray("min"));
                int[] max = ints(o.getAsJsonArray("max"));
                yield new Shape.Cuboid(Bounds.of(min[0], min[1], min[2], max[0], max[1], max[2]));
            }
            case "polygon" ->
                Shape.Polygon.of(
                        ints(o.getAsJsonArray("xs")),
                        ints(o.getAsJsonArray("zs")),
                        o.get("minY").getAsInt(),
                        o.get("maxY").getAsInt());
            case "cylinder" ->
                new Shape.Cylinder(
                        o.get("cx").getAsInt(),
                        o.get("cz").getAsInt(),
                        o.get("rx").getAsInt(),
                        o.get("rz").getAsInt(),
                        o.get("minY").getAsInt(),
                        o.get("maxY").getAsInt());
            case "sphere" -> {
                int[] c = ints(o.getAsJsonArray("center"));
                yield new Shape.Sphere(c[0], c[1], c[2], o.get("radius").getAsInt());
            }
            case "chunks" -> {
                Set<Long> keys = new HashSet<>();
                for (JsonElement element : o.getAsJsonArray("chunks")) {
                    int[] xz = ints(element.getAsJsonArray());
                    keys.add(Shape.chunkKey(xz[0], xz[1]));
                }
                yield Shape.ChunkSet.of(
                        keys, o.get("minY").getAsInt(), o.get("maxY").getAsInt());
            }
            case "global" -> new Shape.Global();
            default -> throw new IllegalArgumentException("unknown shape kind: " + kind);
        };
    }

    private static JsonArray ints(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    private static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsInt();
        }
        return out;
    }

    private static JsonArray array(JsonObject o, String name) {
        return o.has(name) && o.get(name).isJsonArray() ? o.getAsJsonArray(name) : new JsonArray();
    }

    private static JsonObject object(JsonObject o, String name) {
        return o.has(name) && o.get(name).isJsonObject() ? o.getAsJsonObject(name) : new JsonObject();
    }

    private static @Nullable String string(JsonObject o, String name) {
        return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : null;
    }
}
