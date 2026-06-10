package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarker;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerKind;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSource;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import org.jspecify.annotations.NullMarked;

/**
 * The warp {@link MapMarkerSource}: every server-wide warp in the {@link WarpRepository}, mapped to a
 * {@link MapMarker} with the configured warp icon and the expanded tooltip. Read on refresh (enable / reload)
 * off the main thread, since {@link WarpRepository#all()} touches the (cached) database.
 */
@NullMarked
final class WarpMarkerSource implements MapMarkerSource {

    private final WarpRepository warps;
    private final MapMarkerSettings settings;

    WarpMarkerSource(WarpRepository warps, MapMarkerSettings settings) {
        this.warps = Objects.requireNonNull(warps, "warps");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public MapMarkerKind kind() {
        return MapMarkerKind.WARP;
    }

    @Override
    public List<MapMarker> currentMarkers() {
        return warps.all().stream().map(this::toMarker).toList();
    }

    private MapMarker toMarker(Warp warp) {
        String name = warp.name().value();
        Position at = warp.location();
        return MapMarker.of(
                MapMarkerKind.WARP, name, settings.tooltipFor(name), at.world().name(), at.x(), at.y(), at.z());
    }
}
