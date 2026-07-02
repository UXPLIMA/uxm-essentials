package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
 *
 * <p>The animation clock is global, not per-player: this loop calls {@link AnimationRegistry#advance()} exactly once per
 * tick on the loop thread — stepping every named animation to the tick's frame — <em>before</em> the per-player fan-out.
 * Each player then renders against the same captured frame, so an animation advances at most once per tick no matter how
 * many viewers are online and never flickers between viewers within a tick.
 */
@NullMarked
public final class ScoreboardRenderTask {

    private final Scheduler scheduler;
    private final ScoreboardRenderer renderer;
    private final AnimationRegistry animations;
    private final Logger log;
    private final Supplier<Duration> interval;
    private final BooleanSupplier running;

    public ScoreboardRenderTask(
            Scheduler scheduler,
            ScoreboardRenderer renderer,
            AnimationRegistry animations,
            Logger log,
            Supplier<Duration> interval,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.log = Objects.requireNonNull(log, "log");
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
        try {
            // Advance the global animation clock once, on this loop thread, before fanning out. Every per-player render
            // below then reads the same frame for this tick.
            animations.advance();
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.onEntity(BukkitRefs.toRef(player), () -> renderer.renderFor(player));
            }
        } catch (RuntimeException failure) {
            // A throwing tick must not skip the reschedule below, or the board would stop updating until a reload.
            log.error("scoreboard render tick failed", failure);
        }
        scheduleNext();
    }
}
