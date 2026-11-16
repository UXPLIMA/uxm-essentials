package com.uxplima.uxmessentials.moderation.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Stores a ban without letting it overwrite a ban it was not decided against. The running ban is read, the caller's
 * rule says whether it must be kept, and the new ban is written only while the row is still the one that was read; a
 * write that lost a race reads again and decides again. So when two servers ban the same player at once, the second
 * write sees the first and the stronger ban survives, whichever lands first.
 */
final class GuardedBanWrite {

    /**
     * How many times a write that lost a race is retried. Each retry means another ban of this one player landed in
     * the few milliseconds between a read and a write, so running out of attempts is a fault, not contention.
     */
    private static final int ATTEMPTS = 8;

    private GuardedBanWrite() {}

    /** How a guarded write landed. */
    sealed interface Landing permits Stored, Kept {}

    /**
     * The new ban is stored. {@code replacedRunning} says whether a ban was still in force under it, as opposed to no
     * row or one that had already run out.
     */
    record Stored(boolean replacedRunning) implements Landing {}

    /** A ban in force that the rule keeps, so nothing was written. */
    record Kept(TempbanState.Active running) implements Landing {

        Kept {
            Objects.requireNonNull(running, "running");
        }
    }

    /** True when a ban ending at {@code running} lasts past the proposed one: a staff ban never cuts one short. */
    static Predicate<TempbanState.Active> outlasting(TempbanState.Active proposed) {
        Objects.requireNonNull(proposed, "proposed");
        Optional<Instant> end = SanctionStrength.endOf(proposed);
        return running -> SanctionStrength.outlasts(SanctionStrength.endOf(running), end);
    }

    /**
     * True when the proposed ban does not last past the running one: an escalation rung only ever lengthens a ban, and
     * a tie keeps the running one rather than rewrite who issued it.
     */
    static Predicate<TempbanState.Active> notOutlastedBy(TempbanState.Active proposed) {
        Objects.requireNonNull(proposed, "proposed");
        Optional<Instant> end = SanctionStrength.endOf(proposed);
        return running -> !SanctionStrength.outlasts(end, SanctionStrength.endOf(running));
    }

    /**
     * Store {@code ban} for {@code target} unless the ban in force at {@code now} is one {@code keeps} accepts. A row
     * that has already run out is never kept.
     */
    static Landing store(
            ModerationRepository repository,
            PlayerRef target,
            TempbanState.Active ban,
            Instant now,
            Predicate<TempbanState.Active> keeps) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(ban, "ban");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(keeps, "keeps");
        repository.ensureUserExists(target, now);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            TempbanState current = repository.loadTempban(target);
            if (current instanceof TempbanState.Active active && active.isActiveAt(now) && keeps.test(active)) {
                return new Kept(active);
            }
            if (repository.replaceTempban(target, current, ban)) {
                return new Stored(current.isActiveAt(now));
            }
        }
        throw new IllegalStateException("the ban of " + target.uuid() + " kept changing under the write");
    }

    /**
     * Tell {@code actor} which ban kept theirs from landing and how long it still runs, so a staff member who does
     * mean to shorten it knows to lift it first.
     */
    static void tellKept(
            Notifier notifier, PlayerRef actor, PlayerRef target, TempbanState.Active running, Instant now) {
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(running, "running");
        Objects.requireNonNull(now, "now");
        Optional<Instant> end = SanctionStrength.endOf(running);
        if (end.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.BAN_ALREADY_PERMANENT, Map.of("player", target.name()));
            return;
        }
        notifier.send(
                actor,
                ModerationMessageKey.BAN_ALREADY_LONGER,
                Map.of(
                        "player", target.name(),
                        "remaining", SanctionDuration.format(wholeMinutes(Duration.between(now, end.get())))));
    }

    /**
     * {@code left} rounded up to a whole minute: the read and the clock are a moment apart, so seconds would only
     * turn a seven day ban into "6d23h59m58s", and rounding up never claims less than is left.
     */
    private static Duration wholeMinutes(Duration left) {
        long minutes = (left.toSeconds() + 59) / 60;
        return Duration.ofMinutes(minutes);
    }
}
