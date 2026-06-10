package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarker;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerKind;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerPublisher;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.marker.Icon;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

/**
 * Renders {@link MapMarker}s onto squaremap. Every {@code xyz.jpenilla.squaremap} symbol is reached only
 * through this class, constructed only past the squaremap-present guard in {@link MapMarkerPublishers}, so a
 * server without squaremap never loads these classes.
 *
 * <p>squaremap layers are per-world: each marker lives in a {@link SimpleLayerProvider} registered into the
 * owning {@link MapWorld}'s layer registry under the configured layer key. The provider is created lazily the
 * first time a world is touched. A marker's stable id (e.g. {@code warp:shop}) is its squaremap {@link Key},
 * so a re-publish replaces in place and a {@link #remove} drops exactly that icon. {@link #clear} unregisters
 * every layer this plugin registered so a reload re-renders cleanly.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code layers} is a {@link ConcurrentHashMap} keyed by world name;
 * squaremap's own marker registry is thread-safe, so the publisher does not pin a tick thread.
 */
@NullMarked
final class SquaremapMarkerPublisher implements MapMarkerPublisher {

    private static final int ICON_SIZE = 16;
    private static final int LAYER_PRIORITY = 5;

    private final Server server;
    private final MapMarkerSettings settings;
    private final Logger log;
    private final Key layerKey;
    private final Map<String, SimpleLayerProvider> layers = new ConcurrentHashMap<>();

    SquaremapMarkerPublisher(Server server, MapMarkerSettings settings, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.log = Objects.requireNonNull(log, "log");
        this.layerKey = Key.of(sanitize(settings.layerName()));
    }

    @Override
    public void publish(MapMarker marker) {
        Objects.requireNonNull(marker, "marker");
        SimpleLayerProvider layer = layerFor(marker.world());
        if (layer == null) {
            return;
        }
        layer.addMarker(Key.of(sanitize(marker.id())), icon(marker));
    }

    @Override
    public void publishAll(Collection<MapMarker> markers) {
        Objects.requireNonNull(markers, "markers");
        markers.forEach(this::publish);
    }

    @Override
    public void remove(String markerId) {
        Objects.requireNonNull(markerId, "markerId");
        Key key = Key.of(sanitize(markerId));
        layers.values().forEach(layer -> layer.removeMarker(key));
    }

    @Override
    public void clear() {
        Squaremap api = SquaremapProvider.getOrNull(log);
        if (api == null) {
            layers.clear();
            return;
        }
        for (String world : layers.keySet()) {
            mapWorld(api, world).ifPresent(mw -> mw.layerRegistry().unregister(layerKey));
        }
        layers.clear();
    }

    private Icon icon(MapMarker marker) {
        Icon icon = Marker.icon(Point.of(marker.x(), marker.z()), iconKey(marker.kind()), ICON_SIZE);
        MarkerOptions options =
                MarkerOptions.builder().hoverTooltip(marker.label()).build();
        icon.markerOptions(options);
        return icon;
    }

    private Key iconKey(MapMarkerKind kind) {
        return Key.of(sanitize(settings.iconFor(kind)));
    }

    private @org.jspecify.annotations.Nullable SimpleLayerProvider layerFor(String worldName) {
        Squaremap api = SquaremapProvider.getOrNull(log);
        if (api == null) {
            return null;
        }
        return layers.computeIfAbsent(worldName, name -> registerLayer(api, name));
    }

    private @org.jspecify.annotations.Nullable SimpleLayerProvider registerLayer(Squaremap api, String worldName) {
        var mapWorld = mapWorld(api, worldName);
        if (mapWorld.isEmpty()) {
            return null;
        }
        SimpleLayerProvider provider = SimpleLayerProvider.builder(settings.layerName())
                .showControls(true)
                .layerPriority(LAYER_PRIORITY)
                .build();
        mapWorld.get().layerRegistry().register(layerKey, provider);
        return provider;
    }

    private java.util.Optional<MapWorld> mapWorld(Squaremap api, String worldName) {
        World world = server.getWorld(worldName);
        if (world == null) {
            return java.util.Optional.empty();
        }
        return api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world));
    }

    /** squaremap {@link Key}s accept only {@code [a-zA-Z0-9._-]}; fold every other character to {@code _}. */
    private static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
