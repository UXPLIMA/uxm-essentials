package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import java.util.Collection;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarker;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerKind;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerPublisher;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Renders {@link MapMarker}s onto Dynmap. Every {@code org.dynmap} symbol is reached only through this class,
 * constructed only past the Dynmap-present guard in {@link MapMarkerPublishers}, so a server without Dynmap
 * never loads these classes.
 *
 * <p>Dynmap holds all markers in one {@link MarkerSet} (the layer) across every world; a marker carries its
 * world name and coordinates directly. The set is created (or re-found) lazily under the configured layer id.
 * A marker's stable id (e.g. {@code warp:shop}) is its Dynmap marker id, so a re-publish moves the existing
 * marker in place and a {@link #remove} deletes exactly it. Markers are non-persistent: this plugin owns the
 * truth (warps/spawns/homes in the database) and re-renders them on every enable, so Dynmap need not persist
 * a stale copy across restarts. {@link #clear} deletes the whole set so a reload re-renders cleanly.
 */
@NullMarked
final class DynmapMarkerPublisher implements MapMarkerPublisher {

    private final DynmapCommonAPI api;
    private final MapMarkerSettings settings;
    private final Logger log;
    private final String layerId;

    DynmapMarkerPublisher(DynmapCommonAPI api, MapMarkerSettings settings, Logger log) {
        this.api = Objects.requireNonNull(api, "api");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.log = Objects.requireNonNull(log, "log");
        this.layerId = "uxmessentials_" + sanitize(settings.layerName());
    }

    @Override
    public void publish(MapMarker marker) {
        Objects.requireNonNull(marker, "marker");
        MarkerSet set = markerSet();
        if (set == null) {
            return;
        }
        MarkerIcon icon = icon(marker.kind());
        Marker existing = set.findMarker(marker.id());
        if (existing == null) {
            set.createMarker(
                    marker.id(), marker.label(), marker.world(), marker.x(), marker.y(), marker.z(), icon, false);
            return;
        }
        existing.setLocation(marker.world(), marker.x(), marker.y(), marker.z());
        existing.setLabel(marker.label());
    }

    @Override
    public void publishAll(Collection<MapMarker> markers) {
        Objects.requireNonNull(markers, "markers");
        markers.forEach(this::publish);
    }

    @Override
    public void remove(String markerId) {
        Objects.requireNonNull(markerId, "markerId");
        MarkerSet set = markerSet();
        if (set == null) {
            return;
        }
        Marker existing = set.findMarker(markerId);
        if (existing != null) {
            existing.deleteMarker();
        }
    }

    @Override
    public void clear() {
        MarkerAPI markers = markerApi();
        if (markers == null) {
            return;
        }
        MarkerSet set = markers.getMarkerSet(layerId);
        if (set != null) {
            set.deleteMarkerSet();
        }
    }

    private MarkerIcon icon(MapMarkerKind kind) {
        MarkerAPI markers = Objects.requireNonNull(markerApi(), "marker api");
        MarkerIcon icon = markers.getMarkerIcon(settings.iconFor(kind));
        return icon != null ? icon : markers.getMarkerIcon("default");
    }

    private @Nullable MarkerSet markerSet() {
        MarkerAPI markers = markerApi();
        if (markers == null) {
            return null;
        }
        MarkerSet existing = markers.getMarkerSet(layerId);
        if (existing != null) {
            return existing;
        }
        return markers.createMarkerSet(layerId, settings.layerName(), null, false);
    }

    private @Nullable MarkerAPI markerApi() {
        if (!api.markerAPIInitialized()) {
            log.warn("event=dynmap_marker_api_unavailable");
            return null;
        }
        return api.getMarkerAPI();
    }

    private static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
