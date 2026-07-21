package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.AbstractHudRenderTask;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player scoreboard render loop (docs/02-concurrency.md §6.10, matching the tablist render timer). The
 * shared {@link AbstractHudRenderTask} owns the self-rescheduling loop, the global animation-clock advance and the
 * per-player entity-thread fan-out; this class supplies only the scoreboard render call through the
 * {@link ScoreboardRenderer}.
 *
 * <p>The interval is read fresh from the live {@link com.uxplima.uxmessentials.scoreboard.domain.DisplayContent}
 * each reschedule, so a {@code /uxmess reload scoreboard} that swaps a new cadence in changes the refresh rate on
 * the next tick without re-arming the task.
 */
@NullMarked
public final class ScoreboardRenderTask extends AbstractHudRenderTask {

    private final ScoreboardRenderer renderer;

    public ScoreboardRenderTask(
            Scheduler scheduler,
            ScoreboardRenderer renderer,
            AnimationRegistry animations,
            Logger log,
            Supplier<Duration> interval,
            BooleanSupplier running) {
        super(scheduler, animations, log, interval, running);
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    protected void renderFor(Player player) {
        renderer.renderFor(player);
    }

    @Override
    protected String tickFailureMessage() {
        return "scoreboard render tick failed";
    }
}
