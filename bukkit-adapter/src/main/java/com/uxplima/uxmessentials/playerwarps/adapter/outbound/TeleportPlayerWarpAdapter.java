package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry;
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
    private final WarpTeleportRegistry registry;

    public TeleportPlayerWarpAdapter(TeleportEngine engine, WarpTeleportRegistry registry) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void teleportTo(PlayerRef who, PlayerWarp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        registry.register(who.uuid(), warp);
        var warmup = warp.timing().warmupSeconds().map(sec -> java.time.Duration.ofMillis((long) (sec * 1000)));
        var cooldown = warp.timing().cooldownSeconds().map(sec -> java.time.Duration.ofMillis((long) (sec * 1000)));
        engine.launch(who, Destination.at(warp.location(), warmup, cooldown), TeleportKind.WARP);
    }
}
