package com.uxplima.uxmessentials.tablist.adapter.outbound;

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
 * The per-player tablist render loop (docs/02-concurrency.md §6.10, matching the scoreboard render timer). The
 * shared {@link AbstractHudRenderTask} owns the self-rescheduling loop, the global animation-clock advance and the
 * per-player entity-thread fan-out; this class supplies only the tablist render call through the
 * {@link TablistRenderer}.
 *
 * <p>The interval is read fresh from the live {@link com.uxplima.uxmessentials.tablist.domain.TablistContent} each
 * reschedule, so a {@code /uxmess reload tablist} that swaps a new cadence in changes the refresh rate on the next
 * tick without re-arming the task.
 */
@NullMarked
public final class TablistRenderTask extends AbstractHudRenderTask {

    private final TablistRenderer renderer;

    public TablistRenderTask(
            Scheduler scheduler,
            TablistRenderer renderer,
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
        return "tablist render tick failed";
    }
}
