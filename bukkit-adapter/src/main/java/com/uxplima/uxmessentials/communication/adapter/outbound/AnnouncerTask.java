package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerSchedule;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The rotating announcer: a self-rescheduling task on the {@link Scheduler} port (docs/02-concurrency.md §6.10
 * self-rescheduling-loop pattern, matching the presence AFK sweep and the messaging mail-expiry sweep). Each tick
 * asks {@link NextAnnouncement#pick(int)} for the next line given the current online count; when a line is
 * returned it is broadcast to every opted-in player through the {@link BukkitAnnouncerBroadcaster}, otherwise the
 * tick is skipped (no line configured, or below the min-players gate).
 *
 * <p>The interval is read fresh from the live {@link AnnouncerSchedule} each reschedule, so a
 * {@code /uxmess reload communication} that swaps a new schedule in changes the cadence on the next tick without
 * re-arming the task. The task observes the module's {@code running} flag and exits cleanly on disable; the
 * broadcast scan reads the live online set off the tick thread and delivers through the sink, which hops to each
 * viewer's region thread, so no Bukkit entity is touched on the async loop itself.
 *
 * <p>An announcement now carries one or more lines; this task joins them with a newline and broadcasts the block
 * on the chat channel. Per-channel delivery, per-viewer condition gating, and per-announcement interval overrides
 * are the follow-up adapter task; here the legacy single-block chat behaviour is preserved.
 */
@NullMarked
public final class AnnouncerTask {

    private final Scheduler scheduler;
    private final NextAnnouncement nextAnnouncement;
    private final BukkitAnnouncerBroadcaster broadcaster;
    private final Supplier<AnnouncerSchedule> schedule;
    private final BooleanSupplier running;

    public AnnouncerTask(
            Scheduler scheduler,
            NextAnnouncement nextAnnouncement,
            BukkitAnnouncerBroadcaster broadcaster,
            Supplier<AnnouncerSchedule> schedule,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.nextAnnouncement = Objects.requireNonNull(nextAnnouncement, "nextAnnouncement");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.running = Objects.requireNonNull(running, "running");
    }

    /** Arm the first announcer tick; subsequent ticks reschedule themselves until the module stops. */
    public void start() {
        scheduleNext();
    }

    private void scheduleNext() {
        if (!running.getAsBoolean()) {
            return;
        }
        Duration interval = Objects.requireNonNull(schedule.get(), "schedule").interval();
        scheduler.asyncAfter(interval, this::tick);
    }

    private void tick() {
        if (!running.getAsBoolean()) {
            return;
        }
        Optional<Announcement> announcement = nextAnnouncement.pick(broadcaster.onlineCount());
        announcement.ifPresent(picked -> broadcaster.broadcast(String.join("\n", picked.lines())));
        scheduleNext();
    }
}
