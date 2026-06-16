package com.uxplima.uxmessentials.holograms.application;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram info <name>}: print a hologram's stored properties to the operator — its location, line
 * count, type, billboard, background, scale, view range, visibility, refresh interval and rotation — as one
 * header followed by structured {@link HologramsMessageKey} entries (the values interpolate the data). A name no
 * hologram exists at is rejected with {@link HologramError#NOT_FOUND}. The operator-only permission is enforced
 * at the adapter gate.
 */
public final class DescribeHologram {

    private static final String NO_OVERRIDE = "default";
    private static final String UNLIMITED = "unlimited";

    private final HologramRepository repository;
    private final HologramNotifier notifier;

    public DescribeHologram(HologramRepository repository, HologramNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Print every property of the hologram {@code name} to {@code viewer}, or reject when no such hologram. */
    public Result<Unit, HologramError> describe(PlayerRef viewer, HologramName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(viewer, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        emit(viewer, existing.get());
        return Result.ok();
    }

    private void emit(PlayerRef viewer, Hologram hologram) {
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_HEADER,
                Map.of("name", hologram.name().value()));
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_LOCATION, location(hologram.location()));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_LINES,
                Map.of("lines", Integer.toString(hologram.lineCount())));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_TYPE,
                Map.of("type", hologram.type().name()));
        emitAppearance(viewer, hologram.appearance());
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_VISIBILITY, visibility(hologram.visibility()));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_REFRESH,
                Map.of("ticks", Integer.toString(hologram.refreshIntervalTicks())));
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_ROTATION, rotation(hologram.rotation()));
    }

    private void emitAppearance(PlayerRef viewer, Appearance appearance) {
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_BILLBOARD,
                Map.of("billboard", appearance.billboard().name()));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_BACKGROUND,
                Map.of("background", appearance.hasBackground() ? argbHex(appearance.backgroundArgb()) : NO_OVERRIDE));
        notifier.send(
                viewer, HologramsMessageKey.HOLOGRAM_INFO_SCALE, Map.of("scale", Float.toString(appearance.scale())));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_VIEW_RANGE,
                Map.of("range", Float.toString(appearance.viewRange())));
    }

    private static Map<String, String> location(Position at) {
        return Map.of(
                "world", at.world().name(),
                "x", String.format(Locale.ROOT, "%.2f", at.x()),
                "y", String.format(Locale.ROOT, "%.2f", at.y()),
                "z", String.format(Locale.ROOT, "%.2f", at.z()));
    }

    private static Map<String, String> visibility(Visibility visibility) {
        return Map.of(
                "mode", visibility.mode().name(),
                "permission", visibility.permission() == null ? "" : visibility.permission(),
                "distance", visibility.hasDistanceLimit() ? Integer.toString(visibility.distance()) : UNLIMITED);
    }

    private static Map<String, String> rotation(Rotation rotation) {
        return Map.of("yaw", Float.toString(rotation.yaw()), "pitch", Float.toString(rotation.pitch()));
    }

    private static String argbHex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }
}
