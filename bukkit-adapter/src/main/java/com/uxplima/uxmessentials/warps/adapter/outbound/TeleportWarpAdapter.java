package com.uxplima.uxmessentials.warps.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.shared.adapter.outbound.PlayerThread;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry;
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
    private final WarpTeleportRegistry registry;
    private final Scheduler scheduler;

    public TeleportWarpAdapter(TeleportEngine engine, WarpTeleportRegistry registry, Scheduler scheduler) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean admits(PlayerRef who, Warp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        return engine.gateHop(who, destinationOf(warp), TeleportKind.WARP).isOk();
    }

    @Override
    public CompletionStage<TeleportOutcome> teleportTo(PlayerRef who, Warp warp) {
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
    public void arrive(PlayerRef who, Warp warp, Runnable onLanded, Runnable onMissed) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(onLanded, "onLanded");
        Objects.requireNonNull(onMissed, "onMissed");
        registry.register(who.uuid(), warp);
        // The warp's own cooldown rides along so the landing stamps the key the origin's gate checked.
        if (engine.arriveImmediately(who, destinationOf(warp), TeleportKind.WARP, onLanded, onMissed)
                .isErr()) {
            // No hop was issued, so the landing notice registered above would otherwise greet the next teleport.
            registry.getAndRemove(who.uuid());
            onMissed.run();
        }
    }

    private static Destination destinationOf(Warp warp) {
        var warmup = warp.warmupOverrideSeconds().map(sec -> java.time.Duration.ofMillis((long) (sec * 1000)));
        return Destination.at(warp.location(), warmup, warp.ownCooldown());
    }
}
