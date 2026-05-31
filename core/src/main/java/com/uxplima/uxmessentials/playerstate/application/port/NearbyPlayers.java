package com.uxplima.uxmessentials.playerstate.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for {@code /near}: the players within a radius of a viewer, ordered nearest-first. The
 * adapter reads the viewer's live location and scans the same world on the viewer's region thread, mapping
 * each hit to a {@link Nearby} (the ref plus the integer block distance) so the use case stays free of Bukkit.
 * Application code never iterates {@code Bukkit.getOnlinePlayers()} — it asks this port.
 */
public interface NearbyPlayers {

    /** Players within {@code radius} blocks of {@code viewer} (excluding the viewer), nearest first. */
    List<Nearby> within(PlayerRef viewer, int radius);

    /**
     * One nearby player and their block distance from the viewer.
     *
     * @param who the nearby player
     * @param distance the rounded block distance from the viewer
     */
    record Nearby(PlayerRef who, int distance) {}
}
