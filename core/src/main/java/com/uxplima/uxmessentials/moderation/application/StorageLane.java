package com.uxplima.uxmessentials.moderation.application;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.permission.PermissionScope;
import com.uxplima.uxmessentials.shared.application.permission.PermissionSnapshot;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import org.jspecify.annotations.Nullable;

/**
 * Runs the work a moderation command or menu click asks for on the async lane, so its database reads and writes never
 * hold up the server thread. The use cases it runs already reach the running server only through ports that hop to the
 * right thread themselves: a kick runs on the kicked player's own thread once the ban is stored, a broadcast on the
 * global thread, and every line a player reads is delivered on that player's thread in the order it was sent.
 *
 * <p>The permission questions a sanction asks (is the target immune, how long may this staff member ban or mute for)
 * are answered on the calling thread before the work leaves it, and the work runs with those answers bound in the
 * {@link PermissionScope}, since the permission plugin cannot be read safely from a worker.
 *
 * <p>Work about one player runs in the order it was asked for: a mute and the unmute right after it are stored in that
 * order, whichever worker picks them up. Work about nobody in particular is not ordered.
 *
 * <p>A player who leaves before the write lands is not kicked, since there is nobody to kick, but the sanction is
 * stored all the same, so the login check refuses them next time; one who comes back before it lands is kicked when
 * it does. A failure is logged and tells the staff member the command did not go through.
 */
public final class StorageLane {

    private final Scheduler scheduler;
    private final Notifier notifier;
    private final Permissions permissions;
    private final Logger log;
    private final ConcurrentMap<UUID, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    public StorageLane(Scheduler scheduler, Notifier notifier, Permissions permissions, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Run {@code work} for {@code actor} on the async lane, in no particular order. Call it on the server thread. */
    public void run(PlayerRef actor, Runnable work) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(work, "work");
        PermissionSnapshot answers = LanePermissions.capture(permissions, actor, null);
        scheduler.async(() -> guarded(actor, answers, work));
    }

    /**
     * Run {@code work} by {@code actor} about {@code target} on the async lane, after any earlier work about the same
     * target. Call it on the server thread.
     */
    public void run(PlayerRef actor, PlayerRef target, Runnable work) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(work, "work");
        PermissionSnapshot answers = LanePermissions.capture(permissions, actor, target);
        after(target.uuid(), actor, () -> guarded(actor, answers, work), dropped -> {});
    }

    /**
     * Like {@link #run(PlayerRef, PlayerRef, Runnable)}, for a caller that reports the outcome itself: the returned
     * future completes with what {@code work} returns, or with what it threw.
     */
    public <T> CompletableFuture<T> supply(PlayerRef actor, PlayerRef target, Supplier<T> work) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(work, "work");
        PermissionSnapshot answers = LanePermissions.capture(permissions, actor, target);
        CompletableFuture<T> answer = new CompletableFuture<>();
        after(
                target.uuid(),
                actor,
                () -> PermissionScope.runWith(answers, () -> {
                    try {
                        answer.complete(work.get());
                    } catch (RuntimeException failure) {
                        answer.completeExceptionally(failure);
                    }
                }),
                answer::completeExceptionally);
        return answer;
    }

    /**
     * Queue {@code task} behind the last task for {@code key}. The chain is only linked inside {@code compute}; the
     * gate that lets it start is opened after, so no work ever runs while the map entry is locked. The task itself
     * never throws, so a failed link means the scheduler refused it (it does while the server shuts down): that is
     * logged and handed to {@code dropped}.
     */
    private void after(UUID key, PlayerRef actor, Runnable task, Consumer<Throwable> dropped) {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> tail =
                tails.compute(key, (unused, previous) -> chained(gate, previous).thenRunAsync(task, scheduler::async));
        gate.complete(null);
        var unused = tail.whenComplete((ignored, failure) -> {
            tails.remove(key, tail);
            if (failure != null) {
                log.error("event=moderation_lane_dropped actor=" + actor.name(), failure);
                dropped.accept(failure);
            }
        });
    }

    private static CompletableFuture<@Nullable Object> chained(
            CompletableFuture<Void> gate, @Nullable CompletableFuture<Void> previous) {
        CompletableFuture<Void> before = previous == null ? gate : gate.thenCompose(ignored -> previous);
        return before.handle((ignored, failure) -> null);
    }

    private void guarded(PlayerRef actor, PermissionSnapshot answers, Runnable work) {
        try {
            PermissionScope.runWith(answers, work);
        } catch (RuntimeException failure) {
            log.error("event=moderation_lane_failed actor=" + actor.name(), failure);
            notifier.send(actor, SharedMessageKey.COMMAND_ERROR);
        }
    }
}
