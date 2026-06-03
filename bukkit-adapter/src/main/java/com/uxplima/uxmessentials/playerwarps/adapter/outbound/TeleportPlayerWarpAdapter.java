package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code PlayerWarpTeleporter} implementation that <em>delegates</em> a player-warp teleport to the
 * teleport context's {@link TeleportEngine}. Player-warps resolves which {@link PlayerWarp} the player asked
 * for and gates access (ownership, then the public flag); this adapter hands the warp's position to the
 * engine's gated {@code launch}, so the shared teleport cooldown, the move-cancellable warmup, and the
 * region-aware async hop are all the teleport context's concern. The player-warps context owns no movement
 * code of its own.
 *
 * <p>The hop is attributed to {@link TeleportKind#WARP} so the cooldown/warmup tier and the audit verb match
 * the server-warp ones; the engine's cooldown gate result is intentionally ignored here because the engine
 * already notifies the player when a cooldown is active.
 */
@NullMarked
public final class TeleportPlayerWarpAdapter implements PlayerWarpTeleporter {

    private final TeleportEngine engine;

    public TeleportPlayerWarpAdapter(TeleportEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public void teleportTo(PlayerRef who, PlayerWarp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        engine.launch(who, Destination.at(warp.location()), TeleportKind.WARP);
    }
}
