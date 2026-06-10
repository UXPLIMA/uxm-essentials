package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerPublisher;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Discovers which supported map plugin is installed and binds the matching {@link MapMarkerPublisher}, or the
 * no-op publisher when neither is present. This is the only place that reaches a map-plugin SDK through the
 * plugin manager; the SDK imports are confined to {@link DynmapMarkerPublisher} / {@link
 * SquaremapMarkerPublisher}, each constructed only past its plugin-present guard, mirroring how {@code
 * ForeignEconomyProviders} discovers Treasury/Vault.
 *
 * <p>squaremap is preferred when both are installed (its API is the cleaner per-world model); a server with
 * neither map plugin gets {@link MapMarkerPublisher#noOp()}, so the integration wires the same way regardless
 * of what is present.
 */
@NullMarked
public final class MapMarkerPublishers {

    private static final String DYNMAP = "dynmap";
    private static final String SQUAREMAP = "squaremap";

    private MapMarkerPublishers() {}

    /** True when a supported map plugin is installed, so the integration has somewhere to render. */
    public static boolean anyPresent(Server server) {
        Objects.requireNonNull(server, "server");
        return present(server, SQUAREMAP) || present(server, DYNMAP);
    }

    /** The publisher to render through: squaremap first, then Dynmap, then the no-op fallback. */
    public static MapMarkerPublisher discover(Server server, MapMarkerSettings settings, Logger log) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(log, "log");
        if (present(server, SQUAREMAP)) {
            log.info("event=map_markers_backend backend=squaremap layer={}", settings.layerName());
            return new SquaremapMarkerPublisher(server, settings, log);
        }
        Plugin dynmap = server.getPluginManager().getPlugin("dynmap");
        if (dynmap instanceof org.dynmap.DynmapCommonAPI api) {
            log.info("event=map_markers_backend backend=dynmap layer={}", settings.layerName());
            return new DynmapMarkerPublisher(api, settings, log);
        }
        return MapMarkerPublisher.noOp();
    }

    private static boolean present(Server server, String name) {
        Plugin plugin = server.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }
}
