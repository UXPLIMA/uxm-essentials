package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The per-wearer nametag render loop: a self-rescheduling task on the {@link Scheduler} port (docs/02-concurrency.md
 * §6.10 self-rescheduling-loop pattern, matching the tablist and scoreboard render timers). Each tick scans the live
 * online set off the tick thread and, for each player, hops to that player's region/entity thread before touching the
 * live {@code Player} or its display entity through the {@link NametagRenderer} — no Bukkit entity is touched on the
 * async loop itself.
 *
 * <p>Re-selecting the format every tick is what makes a permission, world, gamemode, sneak, or vanish change take
 * effect: {@link NametagRenderer#update} re-selects and respawns when the selected format (or its appearance) changed,
 * recomputes the animated text otherwise, and refreshes the eligible viewer set. The interval is read fresh each
 * reschedule, so a reload that swaps a new cadence in changes the refresh rate on the next tick. The task observes the
 * module's {@code running} flag and exits cleanly on disable.
 *
 * <p>The animation clock is global, not per-player: this loop calls {@link AnimationRegistry#advance()} exactly once
 * per tick on the loop thread — stepping every named animation to the tick's frame — before the per-player fan-out, so
 * an animation advances at most once per tick no matter how many wearers are online (mirrors the other HUD timers).
 */
@NullMarked
public final class NametagRenderTask {

    private final Scheduler scheduler;
    private final NametagRenderer renderer;
    private final AnimationRegistry animations;
    private final Supplier<Duration> interval;
    private final BooleanSupplier running;

    public NametagRenderTask(
            Scheduler scheduler,
            NametagRenderer renderer,
            AnimationRegistry animations,
            Supplier<Duration> interval,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.animations = Objects.requireNonNull(animations, "animations");
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
        // Advance the global animation clock once, on this loop thread, before fanning out so every per-wearer render
        // below reads the same frame for this tick.
        animations.advance();
        long frame = animations.tick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.onEntity(BukkitRefs.toRef(player), () -> renderer.update(player, frame));
        }
        scheduleNext();
    }
}
