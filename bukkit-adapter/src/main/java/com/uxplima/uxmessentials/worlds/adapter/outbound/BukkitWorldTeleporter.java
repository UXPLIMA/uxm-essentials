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

    public BukkitWorldTeleporter(TeleportEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public boolean teleport(PlayerRef who, Position to, WorldTeleportCause cause) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(cause, "cause");
        TeleportKind kind =
                switch (cause) {
                    case SPAWN -> TeleportKind.SPAWN;
                    case ADMIN -> TeleportKind.ADMIN;
                };
        return engine.launch(who, Destination.at(to), kind).isOk();
    }
}
