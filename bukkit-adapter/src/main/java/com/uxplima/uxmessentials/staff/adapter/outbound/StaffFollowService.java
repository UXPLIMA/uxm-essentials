package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.StaffNotifier;
import org.jspecify.annotations.NullMarked;

/**
 * The FOLLOW gadget's runtime: a single repeating task on the {@code Scheduler} port that, each tick, teleports
 * every following staff member onto the player they follow. This lives entirely in the adapter — there is no
 * core port, no DB row — because following is transient session work that vanishes with a restart, a quit, or a
 * mode exit.
 *
 * <p>The staff→target sessions ride a {@link ConcurrentHashMap} mutated only through {@code put}/{@code remove}.
 * A session auto-ends when either player goes offline, or when the staff member has left staff mode (the
 * {@code inStaffMode} predicate): the staff member is told through {@link StaffMessageKey#STAFF_FOLLOW_ENDED} on
 * their own entity thread and the session is dropped. The per-staff teleport uses {@code teleportAsync} so it is
 * region-safe under Folia. {@link #shutdown()} cancels the repeating task and clears every session, called on
 * module stop so a disable leaves no following behind.
 */
@NullMarked
public final class StaffFollowService {

    private final Server server;
    private final Scheduler scheduler;
    private final StaffNotifier notifier;
    private final Predicate<UUID> inStaffMode;
    private final ConcurrentHashMap<UUID, UUID> sessions = new ConcurrentHashMap<>();
    private final AutoCloseable task;

    public StaffFollowService(
            Server server,
            Scheduler scheduler,
            StaffNotifier notifier,
            Predicate<UUID> inStaffMode,
            int intervalTicks) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.inStaffMode = Objects.requireNonNull(inStaffMode, "inStaffMode");
        Duration period = Duration.ofMillis(Math.max(1L, intervalTicks) * 50L);
        this.task = scheduler.repeatGlobal(this::tick, period, period);
    }

    /**
     * Start following {@code target} ({@code true} returned) or, if already following, stop ({@code false}). A
     * staff member follows at most one target at a time, so starting a new follow replaces any prior one.
     */
    public boolean toggle(Player staff, Player target) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        UUID staffId = staff.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (targetId.equals(sessions.get(staffId))) {
            sessions.remove(staffId);
            return false;
        }
        sessions.put(staffId, targetId);
        return true;
    }

    /** Stop {@code staffId} following, if they were. */
    public void stop(UUID staffId) {
        sessions.remove(Objects.requireNonNull(staffId, "staffId"));
    }

    /** Whether {@code staffId} is currently following someone. */
    public boolean isFollowing(UUID staffId) {
        return sessions.containsKey(Objects.requireNonNull(staffId, "staffId"));
    }

    /** Cancel the repeating task and drop every session. Called on module stop. */
    public void shutdown() {
        sessions.clear();
        try {
            task.close();
        } catch (Exception e) {
            throw new IllegalStateException("failed to cancel the staff follow task", e);
        }
    }

    /** One pass over the live sessions; visible so a test can drive a single tick deterministically. */
    public void tick() {
        for (Map.Entry<UUID, UUID> session : sessions.entrySet()) {
            advance(session.getKey(), session.getValue());
        }
    }

    private void advance(UUID staffId, UUID targetId) {
        Player staff = server.getPlayer(staffId);
        Player target = server.getPlayer(targetId);
        if (staff == null || target == null || !inStaffMode.test(staffId)) {
            end(staffId, staff);
            return;
        }
        var ignored = staff.teleportAsync(target.getLocation());
    }

    private void end(UUID staffId, @org.jspecify.annotations.Nullable Player staff) {
        sessions.remove(staffId);
        if (staff != null) {
            PlayerRef staffRef = new PlayerRef(staff.getUniqueId(), staff.getName());
            scheduler.onEntity(staffRef, () -> notifier.send(staffRef, StaffMessageKey.STAFF_FOLLOW_ENDED));
        }
    }
}
