package com.uxplima.uxmessentials.shared.application.mapmarker;

import java.util.Collection;
import java.util.Objects;

/**
 * The {@link MapMarkerPublisher} bound when no supported map plugin is installed: every call is a no-op, so
 * the marker-publish path stays present and the wiring never branches on whether Dynmap or squaremap is
 * present. Inputs are still validated so a programming error surfaces the same way it would against a real
 * publisher.
 */
enum NoOpMapMarkerPublisher implements MapMarkerPublisher {
    INSTANCE;

    @Override
    public void publish(MapMarker marker) {
        Objects.requireNonNull(marker, "marker");
    }

    @Override
    public void publishAll(Collection<MapMarker> markers) {
        Objects.requireNonNull(markers, "markers");
    }

    @Override
    public void remove(String markerId) {
        Objects.requireNonNull(markerId, "markerId");
    }

    @Override
    public void clear() {
        // Nothing is rendered, so nothing is cleared.
    }
}
