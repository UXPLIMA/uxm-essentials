package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.adapter.outbound.PlayerThread;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
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
    private final Scheduler scheduler;

    public TeleportPlayerWarpAdapter(TeleportEngine engine, WarpTeleportRegistry registry, Scheduler scheduler) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean admits(PlayerRef who, PlayerWarp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        return engine.gateHop(who, destinationOf(warp), TeleportKind.WARP).isOk();
    }

    @Override
    public CompletionStage<TeleportOutcome> teleportTo(PlayerRef who, PlayerWarp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        registry.register(who.uuid(), warp);
        CompletableFuture<TeleportOutcome> outcome = new CompletableFuture<>();
        // The use case runs on the async lane, but the launch reads where the player stands and what they may do,
        // which only their own region thread may ask. A player who left before the hop lands hears nothing and their
        // arrival notice is dropped, so it cannot play on a later, unrelated teleport; the outcome still says the
        // teleport never started, so a charge taken for it comes back.
        PlayerThread.run(
                scheduler,
                who,
                () -> engine.launch(who, destinationOf(warp), TeleportKind.WARP, outcome::complete),
                () -> {
                    registry.getAndRemove(who.uuid());
                    outcome.complete(TeleportOutcome.RETIRED);
                });
        return outcome;
    }

    @Override
    public void arrive(PlayerRef who, PlayerWarp warp, Runnable onLanded, Runnable onMissed) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(onLanded, "onLanded");
        Objects.requireNonNull(onMissed, "onMissed");
        registry.register(who.uuid(), warp);
        // The warp's own cooldown rides along, so the stamp at any phase hits the key the origin checked.
        if (engine.arriveImmediately(who, destinationOf(warp), TeleportKind.WARP, onLanded, onMissed)
                .isErr()) {
            // No hop was issued, so the landing notice registered above would otherwise greet the next teleport.
            registry.getAndRemove(who.uuid());
            onMissed.run();
        }
    }

    private static Destination destinationOf(PlayerWarp warp) {
        return Destination.at(warp.location(), warp.timing().warmup(), warp.ownCooldown());
    }
}
