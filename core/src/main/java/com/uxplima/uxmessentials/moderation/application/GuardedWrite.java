package com.uxplima.uxmessentials.moderation.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * Stores a ban, a mute or an IP ban without letting it overwrite one it was not decided against. The row is read,
 * the caller's rule says whether the sanction in force must be kept, and the new one is written only while the row
 * is still the one that was read; a write that lost a race reads again and decides again. So when two servers (or
 * two region threads) sanction the same player or address at once, the second write sees the first and the rule
 * holds whichever lands first.
 */
final class GuardedWrite {

    /**
     * How many times a write that lost a race is retried. Each retry means another sanction of this one row landed in
     * the few milliseconds between a read and a write, so running out of attempts is a fault, not contention.
     */
    private static final int ATTEMPTS = 8;

    private GuardedWrite() {}

    /** How a guarded write landed. */
    sealed interface Landing<R> permits Stored, Kept {}

    /**
     * The new sanction is stored. {@code replacedRunning} says whether one was still in force under it, as opposed to
     * no row or one that had already run out.
     */
    record Stored<R>(boolean replacedRunning) implements Landing<R> {}

    /** A sanction in force that the rule keeps, so nothing was written. */
    record Kept<R>(R running) implements Landing<R> {

        Kept {
            Objects.requireNonNull(running, "running");
        }
    }

    /**
     * One sanction row: {@code S} is the state as stored, {@code R} the sanction in force that a rule compares
     * against.
     */
    interface Row<S, R> {

        /** The row as it stands now, a lapsed sanction included. */
        S read();

        /** The sanction {@code state} holds in force at {@code now}, if any. */
        Optional<R> inForce(S state, Instant now);

        /** Write the new sanction only while the row is still {@code expected}; false when it changed since. */
        boolean replace(S expected);
    }

    /** A player's ban row, written as {@code ban}. */
    static Row<TempbanState, TempbanState.Active> ban(
            ModerationRepository repository, PlayerRef target, TempbanState.Active ban) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(ban, "ban");
        return new Row<>() {
            @Override
            public TempbanState read() {
                return repository.loadTempban(target);
            }

            @Override
            public Optional<TempbanState.Active> inForce(TempbanState state, Instant now) {
                return state instanceof TempbanState.Active active && active.isActiveAt(now)
                        ? Optional.of(active)
                        : Optional.empty();
            }

            @Override
            public boolean replace(TempbanState expected) {
                return repository.replaceTempban(target, expected, ban);
            }

            @Override
            public String toString() {
                return "the ban of " + target.uuid();
            }
        };
    }

    /** A player's mute row, written as {@code mute}, which is never {@link MuteState.None}. */
    static Row<MuteState, MuteState> mute(ModerationRepository repository, PlayerRef target, MuteState mute) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mute, "mute");
        return new Row<>() {
            @Override
            public MuteState read() {
                return repository.loadMute(target);
            }

            @Override
            public Optional<MuteState> inForce(MuteState state, Instant now) {
                return state.isActiveAt(now) ? Optional.of(state) : Optional.empty();
            }

            @Override
            public boolean replace(MuteState expected) {
                return repository.replaceMute(target, expected, mute);
            }

            @Override
            public String toString() {
                return "the mute of " + target.uuid();
            }
        };
    }

    /** An address's IP ban row, written as {@code ban}. */
    static Row<Optional<IpBan>, IpBan> ipBan(ModerationRepository repository, IpBan ban) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(ban, "ban");
        return new Row<>() {
            @Override
            public Optional<IpBan> read() {
                return repository.loadIpBan(ban.ip());
            }

            @Override
            public Optional<IpBan> inForce(Optional<IpBan> state, Instant now) {
                return state.filter(stored -> stored.isActiveAt(now));
            }

            @Override
            public boolean replace(Optional<IpBan> expected) {
                return repository.replaceIpBan(expected, ban);
            }

            @Override
            public String toString() {
                return "the ip ban of " + ban.ip();
            }
        };
    }

    /** Store {@code ban} for {@code target} under {@code keeps}, making sure the player has a row to point at. */
    static Landing<TempbanState.Active> storeBan(
            ModerationRepository repository,
            PlayerRef target,
            TempbanState.Active ban,
            Instant now,
            Predicate<TempbanState.Active> keeps) {
        repository.ensureUserExists(target, now);
        return store(ban(repository, target, ban), now, keeps);
    }

    /** Store {@code mute} for {@code target} under {@code keeps}, making sure the player has a row to point at. */
    static Landing<MuteState> storeMute(
            ModerationRepository repository,
            PlayerRef target,
            MuteState mute,
            Instant now,
            Predicate<MuteState> keeps) {
        repository.ensureUserExists(target, now);
        return store(mute(repository, target, mute), now, keeps);
    }

    /**
     * Tell {@code told} which ban of {@code target} kept theirs from landing, so a staff member who does mean to
     * shorten it knows to lift it first.
     */
    static void tellBanKept(
            Notifier notifier, PlayerRef told, PlayerRef target, TempbanState.Active running, Instant now) {
        tellKept(
                notifier,
                told,
                SanctionStrength.endOf(running),
                now,
                ModerationMessageKey.BAN_ALREADY_PERMANENT,
                ModerationMessageKey.BAN_ALREADY_LONGER,
                Map.of("player", target.name()));
    }

    /**
     * Tell {@code told} which IP ban kept theirs from landing, so a staff member who does mean to shorten it knows to
     * lift it first.
     */
    static void tellIpBanKept(Notifier notifier, PlayerRef told, IpBan running, Instant now) {
        tellKept(
                notifier,
                told,
                running.until(),
                now,
                ModerationMessageKey.BANIP_ALREADY_PERMANENT,
                ModerationMessageKey.BANIP_ALREADY_LONGER,
                Map.of("ip", running.ip()));
    }

    /**
     * Store the row's new sanction unless the one in force at {@code now} is one {@code keeps} accepts. A sanction
     * that has already run out is never kept.
     */
    static <S, R> Landing<R> store(Row<S, R> row, Instant now, Predicate<R> keeps) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(keeps, "keeps");
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            S current = row.read();
            Optional<R> running = row.inForce(current, now);
            if (running.isPresent() && keeps.test(running.get())) {
                return new Kept<>(running.get());
            }
            if (row.replace(current)) {
                return new Stored<>(running.isPresent());
            }
        }
        throw new IllegalStateException(row + " kept changing under the write");
    }

    /**
     * The staff rule: keep a running sanction that lasts past the proposed one ending at {@code proposedEnd}, so a
     * staff sanction never cuts one short. A tie, or a second permanent one, replaces it.
     */
    static <R> Predicate<R> outlasting(Optional<Instant> proposedEnd, Function<R, Optional<Instant>> endOf) {
        Objects.requireNonNull(proposedEnd, "proposedEnd");
        Objects.requireNonNull(endOf, "endOf");
        return running -> SanctionStrength.outlasts(endOf.apply(running), proposedEnd);
    }

    /**
     * The escalation rule: keep a running sanction the proposed one does not last past. A rung only ever lengthens a
     * sanction, and a tie keeps the running one rather than rewrite who issued it.
     */
    static <R> Predicate<R> notOutlastedBy(Optional<Instant> proposedEnd, Function<R, Optional<Instant>> endOf) {
        Objects.requireNonNull(proposedEnd, "proposedEnd");
        Objects.requireNonNull(endOf, "endOf");
        return running -> !SanctionStrength.outlasts(proposedEnd, endOf.apply(running));
    }

    /**
     * Tell {@code told} which sanction kept theirs from landing and how long it still runs: {@code permanent} when it
     * never ends, else {@code timed} with the time left as {@code remaining}. Both get {@code subject}, the
     * placeholders that name the player or address.
     */
    static void tellKept(
            Notifier notifier,
            PlayerRef told,
            Optional<Instant> runningEnd,
            Instant now,
            MessageKey permanent,
            MessageKey timed,
            Map<String, String> subject) {
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(told, "told");
        Objects.requireNonNull(runningEnd, "runningEnd");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(permanent, "permanent");
        Objects.requireNonNull(timed, "timed");
        Objects.requireNonNull(subject, "subject");
        if (runningEnd.isEmpty()) {
            notifier.send(told, permanent, subject);
            return;
        }
        Map<String, String> placeholders = new HashMap<>(subject);
        placeholders.put("remaining", SanctionDuration.format(wholeMinutes(Duration.between(now, runningEnd.get()))));
        notifier.send(told, timed, Map.copyOf(placeholders));
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
