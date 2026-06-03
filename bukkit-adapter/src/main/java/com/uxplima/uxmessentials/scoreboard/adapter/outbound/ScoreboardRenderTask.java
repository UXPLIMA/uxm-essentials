package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player scoreboard render loop: a self-rescheduling task on the {@link Scheduler} port (docs/02-concurrency.md
 * §6.10 self-rescheduling-loop pattern, matching the communication announcer). Each tick scans the live online set
 * off the tick thread and, for each player, hops to that player's region/entity thread before touching the live
 * {@code Player} through the {@link ScoreboardRenderer} — no Bukkit entity is touched on the async loop itself.
 *
 * <p>The interval is read fresh from the live {@link com.uxplima.uxmessentials.scoreboard.domain.DisplayContent} each
 * reschedule, so a {@code /uxmess reload scoreboard} that swaps a new cadence in changes the refresh rate on the next
 * tick without re-arming the task. The task observes the module's {@code running} flag and exits cleanly on disable.
 */
@NullMarked
public final class ScoreboardRenderTask {

    private final Scheduler scheduler;
    private final ScoreboardRenderer renderer;
    private final Supplier<Duration> interval;
    private final BooleanSupplier running;

    public ScoreboardRenderTask(
            Scheduler scheduler, ScoreboardRenderer renderer, Supplier<Duration> interval, BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.running = Objects.requireNonNull(running, "running");
    }

    /** Arm the first render tick; subsequent ticks reschedule themselves until the module stops. */
    public void start() {
        scheduleNext();
    }

    private void scheduleNext() {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.asyncAfter(Objects.requireNonNull(interval.get(), "interval"), this::tick);
    }

    private void tick() {
        if (!running.getAsBoolean()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.onEntity(BukkitRefs.toRef(player), () -> renderer.renderFor(player));
        }
        scheduleNext();
    }
}
