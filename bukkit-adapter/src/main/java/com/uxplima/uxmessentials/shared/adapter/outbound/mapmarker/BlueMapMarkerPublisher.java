package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarker;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerPublisher;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Renders {@link MapMarker}s onto BlueMap, reached <b>entirely by reflection</b>. BlueMap publishes its API to
 * its own repository rather than one this build already declares, so unlike the Dynmap and squaremap
 * publishers there is no {@code compileOnly} dependency behind it: no {@code de.bluecolored} type is named
 * here, only string class names, and a server without BlueMap loads none of its classes.
 *
 * <p>The model mirrors squaremap's rather than the shared-set shape BlueMap examples usually show: one marker
 * set per world, registered into every map BlueMap renders for that world. A single set shared across worlds
 * would put every warp on every world's map, which is wrong the moment a server has more than one world.
 *
 * <p>BlueMap's API only exists once BlueMap itself has enabled, and it disappears again across a BlueMap
 * reload, so {@code BlueMapAPI.getInstance()} answering empty is a normal state and not a failure: the
 * publisher treats it as "not ready", drops that render, and tries again on the next publish. Nothing is
 * cached across an empty answer except the resolved reflective handles.
 *
 * <p><b>Icons.</b> BlueMap addresses marker icons by asset URL, while {@code map-markers.*-icon} holds the
 * short icon keys Dynmap and squaremap use ({@code portal}, {@code house}). Those keys mean nothing to
 * BlueMap, so they are deliberately not passed through and markers render with BlueMap's default POI icon.
 * Applying them would produce broken image links on every marker.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code setsByWorld} is a {@link ConcurrentHashMap} keyed by world
 * name; BlueMap's marker maps are plain maps, so a set is created once under {@code computeIfAbsent} and
 * mutated only from the publish path the marker service already serialises.
 */
@NullMarked
final class BlueMapMarkerPublisher implements MapMarkerPublisher {

    private static final String API_CLASS = "de.bluecolored.bluemap.api.BlueMapAPI";
    private static final String MARKER_SET_CLASS = "de.bluecolored.bluemap.api.markers.MarkerSet";
    private static final String POI_MARKER_CLASS = "de.bluecolored.bluemap.api.markers.POIMarker";

    /** The key our marker set is registered under in every BlueMap map, so a reload replaces it in place. */
    private static final String SET_KEY = "uxmessentials";

    private final Server server;
    private final MapMarkerSettings settings;
    private final Logger log;

    private final Map<String, Object> setsByWorld = new ConcurrentHashMap<>();

    private @Nullable Method getInstance;
    private @Nullable Method getWorld;
    private @Nullable Method getMaps;
    private @Nullable Method getMarkerSets;
    private @Nullable Method getMarkers;
    private boolean unavailableLogged;

    BlueMapMarkerPublisher(Server server, MapMarkerSettings settings, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void publish(MapMarker marker) {
        Objects.requireNonNull(marker, "marker");
        try {
            Object set = setFor(marker.world());
            if (set == null) {
                return;
            }
            markersOf(set).put(marker.id(), poiMarker(marker));
        } catch (Throwable t) {
            degrade("publish", t);
        }
    }

    @Override
    public void publishAll(Collection<MapMarker> markers) {
        Objects.requireNonNull(markers, "markers");
        markers.forEach(this::publish);
    }

    @Override
    public void remove(String markerId) {
        Objects.requireNonNull(markerId, "markerId");
        try {
            for (Object set : setsByWorld.values()) {
                markersOf(set).remove(markerId);
            }
        } catch (Throwable t) {
            degrade("remove", t);
        }
    }

    @Override
    public void clear() {
        try {
            for (Map.Entry<String, Object> entry : setsByWorld.entrySet()) {
                markersOf(entry.getValue()).clear();
                forEachMap(entry.getKey(), map -> markerSetsOf(map).remove(SET_KEY));
            }
        } catch (Throwable t) {
            degrade("clear", t);
        } finally {
            setsByWorld.clear();
        }
    }

    /**
     * The marker set for {@code worldName}, created and registered into that world's maps on first use, or
     * {@code null} when BlueMap has no API instance yet or does not render that world.
     */
    private @Nullable Object setFor(String worldName) throws ReflectiveOperationException {
        Object existing = setsByWorld.get(worldName);
        if (existing != null) {
            return existing;
        }
        Object api = api();
        if (api == null) {
            return null;
        }
        World world = server.getWorld(worldName);
        if (world == null) {
            return null;
        }
        Object created = newMarkerSet();
        boolean registered = forEachMap(worldName, map -> markerSetsOf(map).put(SET_KEY, created));
        if (!registered) {
            // BlueMap knows the world but renders no map for it, or is mid-reload. Do not cache: the set
            // would then never be registered even once BlueMap comes back.
            return null;
        }
        setsByWorld.put(worldName, created);
        return created;
    }

    /**
     * Apply {@code action} to every BlueMap map of {@code worldName}, answering whether any map was visited.
     * A world BlueMap does not render answers {@code false}, which is a normal configuration and not an error.
     */
    private boolean forEachMap(String worldName, MapAction action) throws ReflectiveOperationException {
        Object api = api();
        World world = server.getWorld(worldName);
        if (api == null || world == null) {
            return false;
        }
        Object blueMapWorld = unwrap(getWorld(api).invoke(api, world));
        if (blueMapWorld == null) {
            return false;
        }
        boolean visited = false;
        for (Object map : (Collection<?>) getMaps(blueMapWorld).invoke(blueMapWorld)) {
            action.apply(map);
            visited = true;
        }
        return visited;
    }

    private Object newMarkerSet() throws ReflectiveOperationException {
        Object builder = Class.forName(MARKER_SET_CLASS).getMethod("builder").invoke(null);
        Object labelled = builder.getClass().getMethod("label", String.class).invoke(builder, settings.layerName());
        return labelled.getClass().getMethod("build").invoke(labelled);
    }

    private Object poiMarker(MapMarker marker) throws ReflectiveOperationException {
        Object builder = Class.forName(POI_MARKER_CLASS).getMethod("builder").invoke(null);
        Object labelled = builder.getClass().getMethod("label", String.class).invoke(builder, marker.label());
        Object placed = labelled.getClass()
                .getMethod("position", double.class, double.class, double.class)
                .invoke(labelled, marker.x(), marker.y(), marker.z());
        return placed.getClass().getMethod("build").invoke(placed);
    }

    @SuppressWarnings("unchecked") // BlueMap declares Map<String, Marker>; we only ever put values it built.
    private static Map<String, Object> asStringMap(Object raw) {
        return (Map<String, Object>) raw;
    }

    private Map<String, Object> markersOf(Object markerSet) throws ReflectiveOperationException {
        if (getMarkers == null) {
            getMarkers = markerSet.getClass().getMethod("getMarkers");
        }
        return asStringMap(getMarkers.invoke(markerSet));
    }

    private Map<String, Object> markerSetsOf(Object blueMapMap) throws ReflectiveOperationException {
        if (getMarkerSets == null) {
            getMarkerSets = blueMapMap.getClass().getMethod("getMarkerSets");
        }
        return asStringMap(getMarkerSets.invoke(blueMapMap));
    }

    private Method getWorld(Object api) throws ReflectiveOperationException {
        if (getWorld == null) {
            getWorld = api.getClass().getMethod("getWorld", Object.class);
        }
        return getWorld;
    }

    private Method getMaps(Object blueMapWorld) throws ReflectiveOperationException {
        if (getMaps == null) {
            getMaps = blueMapWorld.getClass().getMethod("getMaps");
        }
        return getMaps;
    }

    /** BlueMap's live API instance, or {@code null} while BlueMap has not enabled or is reloading. */
    private @Nullable Object api() throws ReflectiveOperationException {
        if (getInstance == null) {
            getInstance = Class.forName(API_CLASS).getMethod("getInstance");
        }
        return unwrap(getInstance.invoke(null));
    }

    private static @Nullable Object unwrap(@Nullable Object maybeOptional) {
        if (maybeOptional instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return maybeOptional;
    }

    /**
     * One warning per publisher for an unreachable or incompatible BlueMap, then silence: a map plugin that
     * cannot be reached must not fill the console on every warp creation.
     */
    private void degrade(String operation, Throwable cause) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("event=map_marker_failed backend=bluemap operation={} reason={}", operation, cause);
        }
    }

    /** What {@link #forEachMap} does to each of a world's BlueMap maps. */
    @FunctionalInterface
    private interface MapAction {
        void apply(Object blueMapMap) throws ReflectiveOperationException;
    }
}
