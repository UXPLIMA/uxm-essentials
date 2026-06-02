package com.uxplima.uxmessentials.playerstate.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /playtime [player]}: show a player's total time played. A read-only query through the
 * {@link PlayerInfo} port — nothing is mutated. The viewer sees their own play time, or another player's with
 * the {@code .others} node; an offline target is a silent no-op the adapter has already rejected before this
 * runs. The duration is split into whole days, hours, and minutes for the message placeholders.
 */
public final class ShowPlaytime {

    private static final long MINUTES_PER_DAY = 1440L;
    private static final long MINUTES_PER_HOUR = 60L;

    private final PlayerInfo info;
    private final PlayerStateNotifier notifier;

    public ShowPlaytime(PlayerInfo info, PlayerStateNotifier notifier) {
        this.info = Objects.requireNonNull(info, "info");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Show {@code who} their own play time. */
    public void show(PlayerRef who) {
        showFor(who, who);
    }

    /** Show {@code actor} the play time of {@code subject}. */
    public void showFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Optional<Duration> playtime = info.playtimeOf(subject);
        if (playtime.isEmpty()) {
            return;
        }
        long total = playtime.get().toMinutes();
        Map<String, String> data = Map.of(
                "days", Long.toString(total / MINUTES_PER_DAY),
                "hours", Long.toString((total % MINUTES_PER_DAY) / MINUTES_PER_HOUR),
                "minutes", Long.toString(total % MINUTES_PER_HOUR),
                "player", subject.name());
        notifier.send(
                actor,
                actor.equals(subject) ? PlayerstateMessageKey.PLAYTIME_SHOW : PlayerstateMessageKey.PLAYTIME_SHOW_OTHER,
                data);
    }
}
