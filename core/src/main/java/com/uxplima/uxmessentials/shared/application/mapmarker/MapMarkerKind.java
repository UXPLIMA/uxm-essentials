package com.uxplima.uxmessentials.shared.application.mapmarker;

/**
 * What a {@link MapMarker} represents on the web map. The kind drives which configured icon and which
 * marker-id namespace a marker uses, so a warp named {@code shop} and a per-world spawn named {@code shop}
 * never collide in the map plugin's marker set, and a kind disabled in {@code map-markers} contributes no
 * markers at all.
 */
public enum MapMarkerKind {

    /** A server-wide public warp ({@code /setwarp}). */
    WARP,

    /** A server-wide spawn point ({@code /setspawn}, the main spawn, or a named spawn). */
    SPAWN,

    /** A per-player private home ({@code /sethome}); off by default for privacy. */
    HOME
}
