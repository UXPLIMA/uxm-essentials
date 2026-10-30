package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /playtime import <player|all>} and the join-time import: bring the server's vanilla playtime statistic
 * into the tracked total. The sampler only counts from the day the plugin was installed, so a player who played
 * before then has less tracked time than the server knows of. An import raises the tracked total to the vanilla
 * figure when it is lower and never lowers it, so running it twice changes nothing.
 *
 * <p>Every import marks the player, and the join-time import skips a marked player: a player whose time was reset
 * after an import does not get it back on the next join. The adapter reads the vanilla statistic on the thread
 * that owns it and calls this off the tick thread, since it reads and writes the database.
 */
public final class ImportPlaytime {

    private final PlaytimeRepository repository;
    private final Notifier notifier;
    private final Clock clock;

    public ImportPlaytime(PlaytimeRepository repository, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Import {@code subject}'s vanilla playtime and tell {@code actor} what changed. */
    public void importFor(PlayerRef actor, PlayerRef subject, Duration vanilla) {
        Objects.requireNonNull(actor, "actor");
        Duration added = credit(subject, vanilla);
        Duration total = total(subject);
        if (added.isZero()) {
            notifier.send(
                    actor,
                    PlayerstateMessageKey.PLAYTIME_IMPORT_NOTHING,
                    Map.of("player", subject.name(), "total", PlaytimeFormat.compact(total)));
            return;
        }
        notifier.send(
                actor,
                PlayerstateMessageKey.PLAYTIME_IMPORTED,
                Map.of(
                        "player", subject.name(),
                        "amount", PlaytimeFormat.compact(added),
                        "total", PlaytimeFormat.compact(total)));
    }

    /** Import {@code subject}'s vanilla playtime unless it was imported before. The join-time path. */
    public void importOnJoin(PlayerRef subject, Duration vanilla) {
        Objects.requireNonNull(subject, "subject");
        if (!repository.imported(subject.uuid())) {
            credit(subject, vanilla);
        }
    }

    /**
     * Raise {@code subject}'s tracked total to {@code vanilla} when it is lower, mark them imported, and return the
     * time added (zero when the tracked total already reached it).
     */
    public Duration credit(PlayerRef subject, Duration vanilla) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(vanilla, "vanilla");
        long missing = vanilla.toSeconds() - total(subject).toSeconds();
        if (missing > 0) {
            repository.adjust(subject.uuid(), missing);
        }
        repository.markImported(subject.uuid());
        return Duration.ofSeconds(Math.max(0, missing));
    }

    /** Tell {@code actor} an import of {@code count} players has started. */
    public void startedAll(PlayerRef actor, int count) {
        notifier.send(actor, PlayerstateMessageKey.PLAYTIME_IMPORT_ALL_STARTED, Map.of("count", String.valueOf(count)));
    }

    /** Tell {@code actor} the import finished, with how many of {@code count} players got time added. */
    public void finishedAll(PlayerRef actor, int credited, int count) {
        notifier.send(
                actor,
                PlayerstateMessageKey.PLAYTIME_IMPORT_ALL_DONE,
                Map.of("credited", String.valueOf(credited), "count", String.valueOf(count)));
    }

    private Duration total(PlayerRef subject) {
        return repository.summaryOf(subject.uuid(), LocalDate.now(clock)).totalCombined();
    }
}
