package com.uxplima.uxmessentials.playerstate.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /near [radius]}: list the players within a radius of the viewer, nearest first. The viewer's live
 * scan is the {@link NearbyPlayers} port's job; this use case clamps the radius to a sane bound, asks the
 * port, and renders the header / per-entry / empty feedback through the notifier so all text resolves from
 * {@link PlayerstateMessageKey}.
 */
public final class ListNearby {

    /** The radius used when the command omits one. */
    public static final int DEFAULT_RADIUS = 200;

    /** The largest radius the scan will honour, to bound the cost of a {@code /near} call. */
    public static final int MAX_RADIUS = 5_000;

    private final NearbyPlayers nearby;
    private final Notifier notifier;

    public ListNearby(NearbyPlayers nearby, Notifier notifier) {
        this.nearby = Objects.requireNonNull(nearby, "nearby");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** List players within the default radius of {@code viewer}. */
    public void near(PlayerRef viewer) {
        near(viewer, DEFAULT_RADIUS);
    }

    /**
     * List players within {@code radius} (clamped) blocks of {@code viewer}, pushing the rendered list. The
     * scan resolves on the port's own thread and the rendered lines are then delivered through the notifier
     * (which re-targets each line to the viewer's own thread), so the caller's region thread never blocks on
     * the roster read.
     */
    public void near(PlayerRef viewer, int radius) {
        Objects.requireNonNull(viewer, "viewer");
        int clamped = Math.max(1, Math.min(radius, MAX_RADIUS));
        nearby.within(viewer, clamped, found -> render(viewer, clamped, found));
    }

    private void render(PlayerRef viewer, int clamped, List<NearbyPlayers.Nearby> found) {
        if (found.isEmpty()) {
            notifier.send(viewer, PlayerstateMessageKey.NEAR_EMPTY, Map.of("radius", Integer.toString(clamped)));
            return;
        }
        notifier.send(
                viewer,
                PlayerstateMessageKey.NEAR_HEADER,
                Map.of("count", Integer.toString(found.size()), "radius", Integer.toString(clamped)));
        for (NearbyPlayers.Nearby hit : found) {
            notifier.send(
                    viewer,
                    PlayerstateMessageKey.NEAR_ENTRY,
                    Map.of("player", hit.who().name(), "distance", Integer.toString(hit.distance())));
        }
    }
}
