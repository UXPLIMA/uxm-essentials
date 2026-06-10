package com.uxplima.uxmessentials.shared.application.mapmarker;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The map-markers integration's typed settings, read once from the root {@code config.conf} {@code
 * map-markers} block at wiring time. On by default once a supported map plugin is present, but with
 * <em>homes off</em>: per-player home locations are private, and surfacing them on a public web map is a
 * privacy leak, so {@link #homes()} ships {@code false} and an operator opts in deliberately.
 *
 * <p>The tooltip template is operator-authored config data ({@code <name>} expands to the marker's source
 * name), so it is not a {@code MessageKey}; the icon ids and layer name are passed straight to the map
 * plugin. {@link #renders(MapMarkerKind)} folds the master {@link #enabled()} switch and the per-kind toggle
 * into one check the service consults before it builds any marker of that kind.
 *
 * @param enabled the master switch; {@code false} renders nothing regardless of the per-kind toggles
 * @param warps whether server-wide warps render as markers
 * @param spawns whether server-wide spawns render as markers
 * @param homes whether per-player homes render as markers (off by default for privacy)
 * @param layerName the marker layer name shown in the map plugin's layer control
 * @param warpIcon the icon id for warp markers (map-plugin icon key)
 * @param spawnIcon the icon id for spawn markers (map-plugin icon key)
 * @param homeIcon the icon id for home markers (map-plugin icon key)
 * @param tooltip the marker label/tooltip template; {@code <name>} expands to the source name
 */
public record MapMarkerSettings(
        boolean enabled,
        boolean warps,
        boolean spawns,
        boolean homes,
        String layerName,
        String warpIcon,
        String spawnIcon,
        String homeIcon,
        String tooltip) {

    private static final String ENABLED_PATH = "map-markers.enabled";
    private static final String WARPS_PATH = "map-markers.warps";
    private static final String SPAWNS_PATH = "map-markers.spawns";
    private static final String HOMES_PATH = "map-markers.homes";
    private static final String LAYER_PATH = "map-markers.layer-name";
    private static final String WARP_ICON_PATH = "map-markers.warp-icon";
    private static final String SPAWN_ICON_PATH = "map-markers.spawn-icon";
    private static final String HOME_ICON_PATH = "map-markers.home-icon";
    private static final String TOOLTIP_PATH = "map-markers.tooltip";

    private static final String NAME_TOKEN = "<name>";

    private static final String DEFAULT_LAYER = "uxmEssentials";
    private static final String DEFAULT_WARP_ICON = "portal";
    private static final String DEFAULT_SPAWN_ICON = "world";
    private static final String DEFAULT_HOME_ICON = "house";
    private static final String DEFAULT_TOOLTIP = NAME_TOKEN;

    public MapMarkerSettings {
        Objects.requireNonNull(layerName, "layerName");
        Objects.requireNonNull(warpIcon, "warpIcon");
        Objects.requireNonNull(spawnIcon, "spawnIcon");
        Objects.requireNonNull(homeIcon, "homeIcon");
        Objects.requireNonNull(tooltip, "tooltip");
    }

    /** Read the {@code map-markers} block from {@code config}, falling back to the shipped defaults per key. */
    public static MapMarkerSettings from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new MapMarkerSettings(
                config.getBoolean(ENABLED_PATH, true),
                config.getBoolean(WARPS_PATH, true),
                config.getBoolean(SPAWNS_PATH, true),
                config.getBoolean(HOMES_PATH, false),
                config.getString(LAYER_PATH, DEFAULT_LAYER),
                config.getString(WARP_ICON_PATH, DEFAULT_WARP_ICON),
                config.getString(SPAWN_ICON_PATH, DEFAULT_SPAWN_ICON),
                config.getString(HOME_ICON_PATH, DEFAULT_HOME_ICON),
                config.getString(TOOLTIP_PATH, DEFAULT_TOOLTIP));
    }

    /** Whether markers of {@code kind} render: the master switch AND the kind's own toggle. */
    public boolean renders(MapMarkerKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (!enabled) {
            return false;
        }
        return switch (kind) {
            case WARP -> warps;
            case SPAWN -> spawns;
            case HOME -> homes;
        };
    }

    /** The configured icon id for {@code kind}. */
    public String iconFor(MapMarkerKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case WARP -> warpIcon;
            case SPAWN -> spawnIcon;
            case HOME -> homeIcon;
        };
    }

    /** Expand the tooltip template for a marker named {@code name} ({@code <name>} -> the source name). */
    public String tooltipFor(String name) {
        Objects.requireNonNull(name, "name");
        return tooltip.replace(NAME_TOKEN, name);
    }
}
