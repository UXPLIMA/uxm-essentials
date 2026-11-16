package com.uxplima.uxmessentials.moderation.application;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Reads whether the target of a sanction holds {@link ModerationGuard#EXEMPT_NODE}, on the target's own thread.
 *
 * <p>A player's permissions may only be read on the thread that owns them. On Folia the staff member asking is
 * usually on another region's thread, where the read cannot be made in place and must not wait, so it is sent to the
 * target's thread and answered later. The answer is empty when it could not be read: the target left before their
 * thread ran it, or it did not run within {@link #WAIT}. An empty answer means "possibly exempt", and the sanction is
 * refused rather than risked.
 *
 * <p>A player who is not online here holds no node the server can read, so they are answered as not exempt, the
 * answer an offline sanction has always had.
 */
final class TargetExemption {

    /** How long a target's thread has to answer before the sanction is refused. */
    static final Duration WAIT = Duration.ofSeconds(2);

    private final Permissions live;
    private final Scheduler scheduler;
    private final PlayerLookup players;

    TargetExemption(Permissions live, Scheduler scheduler, PlayerLookup players) {
        this.live = Objects.requireNonNull(live, "live");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
    }

    /** Whether {@code target} is exempt, or empty when that could not be read. Never completes exceptionally. */
    CompletableFuture<Optional<Boolean>> read(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        if (target.isSystem() || !players.isOnline(target.uuid())) {
            return CompletableFuture.completedFuture(Optional.of(false));
        }
        if (scheduler.ownsEntity(target)) {
            return CompletableFuture.completedFuture(live.read(target, ModerationGuard.EXEMPT_NODE));
        }
        CompletableFuture<Optional<Boolean>> answer = new CompletableFuture<>();
        try {
            scheduler.onEntity(
                    target,
                    () -> answer.complete(live.read(target, ModerationGuard.EXEMPT_NODE)),
                    () -> answer.complete(Optional.empty()));
        } catch (RuntimeException refused) {
            // The scheduler refuses work while the plugin stops; nothing will answer.
            answer.complete(Optional.empty());
        }
        return answer.completeOnTimeout(Optional.empty(), WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }
}
