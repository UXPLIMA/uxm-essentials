package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The rotating announcer: self-rescheduling tasks on the {@link Scheduler} port (docs/02-concurrency.md §6.10
 * self-rescheduling-loop pattern, matching the presence AFK sweep and the messaging mail-expiry sweep).
 *
 * <p>There are two cadences. Announcements <em>without</em> an interval override rotate together on the config-wide
 * default interval: each default tick asks {@link NextAnnouncement#pick(int)} (the cursor + ordering + no-repeat
 * rule, gated by min-players) for the next one and broadcasts it. Each announcement that declares its own
 * {@code interval-seconds} runs on its <em>own</em> independent loop at that cadence, re-resolved from the live
 * config by id each tick (so a reload that changes its interval or drops it is honoured) and gated by the same
 * min-players check. An override announcement is therefore not part of the shared rotation.
 *
 * <p>The default interval and the per-announcement overrides are read fresh from the live {@link AnnouncerConfig}
 * each reschedule, so a {@code /announce reload} (or {@code /uxmess reload communication}) that swaps a new config
 * in changes a cadence on the next tick without re-arming the task. Every loop observes the module's {@code running}
 * flag and exits cleanly on disable. Delivery flows through the {@link BukkitAnnouncerBroadcaster}, which enumerates
 * the online set on the global thread and hops to each viewer's region thread, gating by opt-out and the
 * announcement's display condition; no Bukkit entity is touched on the async loop itself.
 */
@NullMarked
public final class AnnouncerTask {

    private final Scheduler scheduler;
    private final NextAnnouncement nextAnnouncement;
    private final BukkitAnnouncerBroadcaster broadcaster;
    private final Supplier<AnnouncerConfig> config;
    private final BooleanSupplier running;

    public AnnouncerTask(
            Scheduler scheduler,
            NextAnnouncement nextAnnouncement,
            BukkitAnnouncerBroadcaster broadcaster,
            Supplier<AnnouncerConfig> config,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.nextAnnouncement = Objects.requireNonNull(nextAnnouncement, "nextAnnouncement");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.config = Objects.requireNonNull(config, "config");
        this.running = Objects.requireNonNull(running, "running");
    }

    /**
     * Arm the default rotation loop and one independent loop per override announcement present at start. A reload
     * that introduces a new override announcement is picked up on the next module wiring; within a running task an
     * override loop re-resolves its announcement from the live config each tick, so an interval change takes effect
     * without re-arming.
     */
    public void start() {
        scheduleDefaultRotation();
        for (Announcement announcement : config.get().announcements()) {
            announcement.intervalOverride().ifPresent(interval -> scheduleOverride(announcement.id(), interval));
        }
    }

    private void scheduleDefaultRotation() {
        if (!running.getAsBoolean()) {
            return;
        }
        Duration interval = config.get().defaultInterval();
        scheduler.asyncAfter(interval, this::tickDefaultRotation);
    }

    private void tickDefaultRotation() {
        if (!running.getAsBoolean()) {
            return;
        }
        Optional<Announcement> picked = nextAnnouncement.pick(broadcaster.onlineCount());
        picked.ifPresent(broadcaster::broadcast);
        scheduleDefaultRotation();
    }

    private void scheduleOverride(String id, Duration interval) {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.asyncAfter(interval, () -> tickOverride(id));
    }

    private void tickOverride(String id) {
        if (!running.getAsBoolean()) {
            return;
        }
        AnnouncerConfig live = config.get();
        Optional<Announcement> current = find(live, id);
        if (current.isEmpty() || current.get().intervalOverride().isEmpty()) {
            return; // the announcement was dropped or lost its override on reload; let this loop die
        }
        Announcement announcement = current.get();
        if (live.shouldFire(broadcaster.onlineCount())) {
            broadcaster.broadcast(announcement);
        }
        scheduleOverride(id, announcement.intervalOverride().orElseThrow());
    }

    private static Optional<Announcement> find(AnnouncerConfig config, String id) {
        return config.announcements().stream()
                .filter(announcement -> announcement.id().equals(id))
                .findFirst();
    }
}
