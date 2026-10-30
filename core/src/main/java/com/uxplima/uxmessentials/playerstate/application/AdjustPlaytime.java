package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /playtime set|add|take <player> <time>}: correct a player's all-time playtime by hand. The correction is
 * stored beside the sampler's per-day ledger (see {@link PlaytimeRepository#adjust}), so the today, week and month
 * windows keep what the sampler counted and only the all-time total moves. A take never goes below zero.
 *
 * <p>Each call reads the current total and writes the difference, so a sample that lands between the two shifts
 * the result by at most one sample interval. Callers run it off the tick thread: it reads and writes the database.
 */
public final class AdjustPlaytime {

    private final PlaytimeRepository repository;
    private final Notifier notifier;
    private final Clock clock;

    public AdjustPlaytime(PlaytimeRepository repository, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Make {@code subject}'s all-time playtime exactly {@code amount}. */
    public void set(PlayerRef actor, PlayerRef subject, String amount) {
        parsed(actor, amount).ifPresent(target -> {
            repository.adjust(subject.uuid(), target.minus(total(subject)).toSeconds());
            notifier.send(
                    actor,
                    PlayerstateMessageKey.PLAYTIME_SET,
                    Map.of("player", subject.name(), "total", PlaytimeFormat.compact(target)));
        });
    }

    /** Add {@code amount} to {@code subject}'s all-time playtime. */
    public void add(PlayerRef actor, PlayerRef subject, String amount) {
        parsed(actor, amount).ifPresent(added -> {
            Duration before = total(subject);
            repository.adjust(subject.uuid(), added.toSeconds());
            changed(actor, subject, PlayerstateMessageKey.PLAYTIME_ADD, added, before.plus(added));
        });
    }

    /** Take {@code amount} from {@code subject}'s all-time playtime, stopping at zero. */
    public void take(PlayerRef actor, PlayerRef subject, String amount) {
        parsed(actor, amount).ifPresent(asked -> {
            Duration before = total(subject);
            Duration taken = asked.compareTo(before) > 0 ? before : asked;
            repository.adjust(subject.uuid(), -taken.toSeconds());
            changed(actor, subject, PlayerstateMessageKey.PLAYTIME_TAKE, taken, before.minus(taken));
        });
    }

    private Optional<Duration> parsed(PlayerRef actor, String amount) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(amount, "amount");
        Optional<Duration> duration = PlaytimeAmount.parse(amount);
        if (duration.isEmpty()) {
            notifier.send(actor, PlayerstateMessageKey.PLAYTIME_BAD_AMOUNT, Map.of("input", amount));
        }
        return duration;
    }

    private Duration total(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        return repository.summaryOf(subject.uuid(), LocalDate.now(clock)).totalCombined();
    }

    private void changed(
            PlayerRef actor, PlayerRef subject, PlayerstateMessageKey key, Duration amount, Duration total) {
        notifier.send(
                actor,
                key,
                Map.of(
                        "player", subject.name(),
                        "amount", PlaytimeFormat.compact(amount),
                        "total", PlaytimeFormat.compact(total)));
    }
}
