package com.uxplima.uxmessentials.moderation.application;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Reads whether the target of a sanction holds {@link ModerationGuard#EXEMPT_NODE}.
 *
 * <p>A player online here is read live, on their own thread. A player's permissions may only be read on the thread
 * that owns them. On Folia the staff member asking is usually on another region's thread, where the read cannot be
 * made in place and must not wait, so it is sent to the target's thread and answered later.
 *
 * <p>A player who is not online here (offline, or on another backend) is read from what the permission plugin has
 * stored for them, off the tick. Being away is no proof of not being exempt: when nothing here can read the node the
 * answer is {@link Answer#NO_SOURCE} and the sanction is refused.
 *
 * <p>Every answer that is not {@link Answer#HELD} or {@link Answer#NOT_HELD} means "possibly exempt", and the
 * sanction is refused rather than risked: the target left before their thread ran the read, a read did not answer in
 * time, or there was nothing to read from.
 */
final class TargetExemption {

    /** How long a target's thread has to answer before the sanction is refused. */
    static final Duration WAIT = Duration.ofSeconds(2);

    /** How long the permission plugin's storage has to answer for a target who is not here. */
    static final Duration STORED_WAIT = Duration.ofSeconds(5);

    private final Permissions live;
    private final StoredPermissions stored;
    private final Scheduler scheduler;
    private final PlayerLookup players;

    TargetExemption(Permissions live, StoredPermissions stored, Scheduler scheduler, PlayerLookup players) {
        this.live = Objects.requireNonNull(live, "live");
        this.stored = Objects.requireNonNull(stored, "stored");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
    }

    /** Whether {@code target} is exempt. Call it on a server thread. Never completes exceptionally. */
    CompletableFuture<Answer> read(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        if (target.isSystem()) {
            return CompletableFuture.completedFuture(Answer.NOT_HELD);
        }
        if (!players.isOnline(target.uuid())) {
            return fromStore(target);
        }
        if (scheduler.ownsEntity(target)) {
            return CompletableFuture.completedFuture(answer(live.read(target, ModerationGuard.EXEMPT_NODE)));
        }
        CompletableFuture<Answer> answer = new CompletableFuture<>();
        try {
            scheduler.onEntity(
                    target,
                    () -> answer.complete(answer(live.read(target, ModerationGuard.EXEMPT_NODE))),
                    () -> answer.complete(Answer.NO_ANSWER));
        } catch (RuntimeException refused) {
            // The scheduler refuses work while the plugin stops; nothing will answer.
            answer.complete(Answer.NO_ANSWER);
        }
        return answer.completeOnTimeout(Answer.NO_ANSWER, WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<Answer> fromStore(PlayerRef target) {
        CompletableFuture<Answer> read;
        try {
            read = stored.read(target, ModerationGuard.EXEMPT_NODE);
        } catch (RuntimeException failed) {
            return CompletableFuture.completedFuture(Answer.NO_ANSWER);
        }
        return read.exceptionally(failed -> Answer.NO_ANSWER)
                .completeOnTimeout(Answer.NO_ANSWER, STORED_WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Answer answer(Optional<Boolean> read) {
        return read.map(held -> held ? Answer.HELD : Answer.NOT_HELD).orElse(Answer.NO_ANSWER);
    }
}
