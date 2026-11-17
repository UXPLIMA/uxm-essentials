package com.uxplima.uxmessentials.moderation.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.permission.PermissionScope;
import com.uxplima.uxmessentials.shared.application.permission.PermissionSnapshot;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import org.jspecify.annotations.Nullable;

/**
 * Runs the work a moderation command or menu click asks for on the async lane, so its database reads and writes never
 * hold up the server thread. The use cases it runs already reach the running server only through ports that hop to the
 * right thread themselves: a kick runs on the kicked player's own thread once the ban is stored, a broadcast on the
 * global thread, and every line a player reads is delivered on that player's thread in the order it was sent.
 *
 * <p>The permission questions a sanction asks are answered before the work runs, and the work runs with those answers
 * bound in the {@link PermissionScope}, since the permission plugin cannot be read safely from a worker. How long the
 * staff member may ban or mute for is read on the calling thread, theirs. Whether the target is immune is read on the
 * target's own thread, which on Folia is often another region's, or, for a target who is not online here, from what
 * the permission plugin stored for them, off the tick. The work waits for that answer before it starts, still in its
 * place in the target's queue. When the answer cannot be had (the target left on the way, their thread or the
 * storage did not answer in time, or nothing here can read a target who is away) the target counts as immune, the
 * staff member is told why, and no sanction lands on a player who may be exempt.
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
    private final TargetExemption exemption;
    private final ConcurrentMap<UUID, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    public StorageLane(
            Scheduler scheduler,
            Notifier notifier,
            Permissions permissions,
            StoredPermissions stored,
            PlayerLookup players,
            Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.log = Objects.requireNonNull(log, "log");
        this.exemption = new TargetExemption(
                permissions,
                Objects.requireNonNull(stored, "stored"),
                scheduler,
                Objects.requireNonNull(players, "players"));
    }

    /** Run {@code work} for {@code actor} on the async lane, in no particular order. Call it on the server thread. */
    public void run(PlayerRef actor, Runnable work) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(work, "work");
        PermissionSnapshot answers = LanePermissions.capture(permissions, actor).build();
        scheduler.async(() -> guarded(actor, answers, work));
    }

    /**
     * Run {@code work} by {@code actor} about each of {@code targets} at once (a kick of everyone online) on the async
     * lane, in no particular order, once every target's immunity has been read on their own thread. Call it on the
     * server thread.
     */
    public void run(PlayerRef actor, Collection<PlayerRef> targets, Runnable work) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(work, "work");
        PermissionSnapshot.Builder answers = LanePermissions.capture(permissions, actor);
        Map<PlayerRef, CompletableFuture<Answer>> exempt = new LinkedHashMap<>();
        for (PlayerRef target : targets) {
            exempt.put(target, exemption.read(target));
        }
        var unused = CompletableFuture.allOf(exempt.values().toArray(CompletableFuture[]::new))
                .thenRunAsync(
                        () -> {
                            exempt.forEach((target, read) -> withExemption(answers, actor, target, read));
                            guarded(actor, answers.build(), work);
                        },
                        scheduler::async)
                .exceptionally(failure -> {
                    log.error("event=moderation_lane_dropped actor=" + actor.name(), failure);
                    return null;
                });
    }

    /**
     * Run {@code work} by {@code actor} about {@code target} on the async lane, after any earlier work about the same
     * target. Call it on the server thread.
     */
    public void run(PlayerRef actor, PlayerRef target, Runnable work) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(work, "work");
        PermissionSnapshot.Builder answers = LanePermissions.capture(permissions, actor);
        CompletableFuture<Answer> exempt = exemption.read(target);
        after(
                target.uuid(),
                actor,
                exempt,
                () -> guarded(
                        actor, withExemption(answers, actor, target, exempt).build(), work),
                dropped -> {});
    }

    /**
     * Run the server's own bookkeeping about {@code player} (their join and quit) on the async lane, in order with
     * every other piece of work about them. Nobody asked for it, so a failure is only logged, and it reads no
     * permissions.
     */
    public void background(PlayerRef player, Runnable work) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(work, "work");
        after(
                player.uuid(),
                player,
                CompletableFuture.completedFuture(null),
                () -> {
                    try {
                        work.run();
                    } catch (RuntimeException failure) {
                        log.error("event=moderation_lane_failed player=" + player.name(), failure);
                    }
                },
                dropped -> {});
    }

    /**
     * Like {@link #run(PlayerRef, PlayerRef, Runnable)}, for a caller that reports the outcome itself: the returned
     * future completes with what {@code work} returns, or with what it threw.
     */
    public <T> CompletableFuture<T> supply(PlayerRef actor, PlayerRef target, Supplier<T> work) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(work, "work");
        PermissionSnapshot.Builder answers = LanePermissions.capture(permissions, actor);
        CompletableFuture<Answer> exempt = exemption.read(target);
        CompletableFuture<T> answer = new CompletableFuture<>();
        after(
                target.uuid(),
                actor,
                exempt,
                () -> PermissionScope.runWith(
                        withExemption(answers, actor, target, exempt).build(), () -> {
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
     * Queue {@code task} behind the last task for {@code key}, to start once {@code ready} has completed too. The task
     * keeps its place: work queued after it waits for it, even while it waits for {@code ready}. The chain is only
     * linked inside {@code compute}; the gate that lets it start is opened after, so no work ever runs while the map
     * entry is locked. The task itself never throws, so a failed link means the scheduler refused it (it does while the
     * server shuts down): that is logged and handed to {@code dropped}.
     */
    private void after(
            UUID key, PlayerRef actor, CompletableFuture<?> ready, Runnable task, Consumer<Throwable> dropped) {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> tail = tails.compute(
                key,
                (unused, previous) -> chained(gate, previous)
                        .thenCombine(ready.handle((value, failure) -> null), (before, readyNow) -> before)
                        .thenRunAsync(task, scheduler::async));
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

    /**
     * The snapshot with the target's immunity added. An immunity that could not be read counts as held, so the
     * sanction is refused, and the staff member is told why rather than only that the player is exempt: to try again
     * when the read only missed, or that it cannot be read here at all while the target is away.
     */
    private PermissionSnapshot.Builder withExemption(
            PermissionSnapshot.Builder answers, PlayerRef actor, PlayerRef target, CompletableFuture<Answer> exempt) {
        Answer read = exempt.getNow(Answer.NO_ANSWER);
        switch (read) {
            case HELD, NOT_HELD -> {}
            case NO_ANSWER -> {
                log.warn("event=moderation_exemption_unread actor={} target={}", actor.name(), target.name());
                notifier.send(actor, ModerationMessageKey.TARGET_EXEMPTION_UNREAD, Map.of("player", target.name()));
            }
            case NO_SOURCE -> {
                log.warn("event=moderation_exemption_unknowable actor={} target={}", actor.name(), target.name());
                notifier.send(actor, ModerationMessageKey.TARGET_EXEMPTION_UNKNOWN, Map.of("player", target.name()));
            }
        }
        return answers.answered(target, ModerationGuard.EXEMPT_NODE, read != Answer.NOT_HELD);
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
