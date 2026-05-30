package com.uxplima.uxmessentials.warps.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code WarpTeleporter} implementation that <em>delegates</em> a warp teleport to the teleport
 * context's {@link TeleportEngine}. Warps resolves which {@link Warp} the player asked for and gates access
 * and cost; this adapter hands the warp's position to the engine's gated {@code launch}, so the shared
 * teleport cooldown, the move-cancellable warmup, and the region-aware async hop are all the teleport
 * context's concern. The warps context owns no movement code of its own.
 *
 * <p>The hop is attributed to {@link TeleportKind#WARP} so the cooldown/warmup tier and the audit verb are
 * the warp ones; the engine's cooldown gate result is intentionally ignored here because the engine already
 * notifies the player when a cooldown is active.
 */
@NullMarked
public final class TeleportWarpAdapter implements WarpTeleporter {

    private final TeleportEngine engine;

    public TeleportWarpAdapter(TeleportEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public void teleportTo(PlayerRef who, Warp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        engine.launch(who, Destination.at(warp.location()), TeleportKind.WARP);
    }
}
