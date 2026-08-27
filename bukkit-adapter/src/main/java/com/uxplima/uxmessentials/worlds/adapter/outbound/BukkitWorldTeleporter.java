package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.worlds.application.port.WorldTeleporter;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import org.jspecify.annotations.NullMarked;

/**
 * The bridge from the worlds context's {@link WorldTeleporter} port to the shared teleport context, so
 * a world-entry teleport runs through the same warmup, cooldown, and move-cancels-warmup machinery as
 * every other teleport. This is the one worlds class permitted to depend on {@code teleport.*}; the
 * worlds domain and application stay free of it.
 */
@NullMarked
public final class BukkitWorldTeleporter implements WorldTeleporter {

    private final TeleportEngine engine;
    private final ForcedWorldEntryMarker marker;

    public BukkitWorldTeleporter(TeleportEngine engine, ForcedWorldEntryMarker marker) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.marker = Objects.requireNonNull(marker, "marker");
    }

    @Override
    public boolean teleport(PlayerRef who, Position to, WorldTeleportCause cause) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(cause, "cause");
        if (cause == WorldTeleportCause.VOID_RESCUE) {
            // A rescue is involuntary: the player is already inside the world, so the access policy has nothing
            // left to decide, and the hop must land at once with no warmup to walk out of and no /back point.
            marker.mark(who.uuid());
            engine.relocateImmediately(who, Destination.at(to));
            return true;
        }
        TeleportKind kind =
                switch (cause) {
                    case SPAWN -> TeleportKind.SPAWN;
                    case ADMIN, VOID_RESCUE -> TeleportKind.ADMIN;
                };
        if (cause == WorldTeleportCause.ADMIN) {
            // A staff /worlds tp (or login redirect) bypasses the access policy in WorldTeleportService;
            // mark the player so the cross-world access listener exempts the event this launch raises. Only
            // ADMIN is instant, so the mark-to-event window is negligible; SPAWN can be cancelled mid-warmup.
            marker.mark(who.uuid());
        }
        return engine.launch(who, Destination.at(to), kind).isOk();
    }
}
