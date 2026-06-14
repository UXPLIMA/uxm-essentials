package com.uxplima.uxmessentials.communication.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * The cadence and content of the rotating announcer. It carries the {@link #interval} between broadcasts, the
 * operator-authored {@link #lines} (MiniMessage templates), the {@link #ordering} that selects among them, and
 * the {@link #minOnlinePlayers} gate below which a tick is skipped — an empty or near-empty server is not spammed
 * with broadcasts no one reads. The schedule is pure data: the {@code NextAnnouncement} use case reads it to
 * decide whether this tick fires and which line it picks, advancing an {@link AnnouncerCursor} across ticks.
 *
 * <p>The flat list-of-lines shape is the back-compatible legacy form. The richer {@link AnnouncerConfig} —
 * a list of per-announcement {@link Announcement}s, each with its own condition, channels, sound, and optional
 * interval — supersedes it; {@link #asConfig()} bridges a flat schedule to one default announcement so the legacy
 * config keeps working until the codec produces an {@code AnnouncerConfig} directly.
 *
 * <p>The schedule is rebuilt atomically on reload (a new {@code AtomicReference<AnnouncerSchedule>} swapped in);
 * the adapter's timer reads the current schedule each tick, so an interval or line change takes effect on the
 * next tick without re-scheduling. The lines are operator content rendered through MiniMessage later — never
 * {@code MessageKey}s and never parity-checked.
 *
 * @param interval the wall-clock gap between announcer ticks; must be positive
 * @param minOnlinePlayers the minimum online count for a tick to fire; zero means "always fire"
 * @param ordering how a tick selects its line ({@link Ordering#RANDOM} pairs with no-immediate-repeat)
 * @param lines the operator-authored broadcast templates
 */
public record AnnouncerSchedule(Duration interval, int minOnlinePlayers, Ordering ordering, List<String> lines) {

    public AnnouncerSchedule {
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(ordering, "ordering");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("announcer interval must be positive: " + interval);
        }
        if (minOnlinePlayers < 0) {
            throw new IllegalArgumentException("minOnlinePlayers must not be negative: " + minOnlinePlayers);
        }
    }

    /** An empty schedule with no lines — the announcer never broadcasts. Used when the feature is configured off. */
    public static AnnouncerSchedule silent(Duration interval) {
        return new AnnouncerSchedule(interval, 0, Ordering.SEQUENTIAL, List.of());
    }

    /** Whether the announcer has any line to broadcast at all. */
    public boolean hasLines() {
        return !lines.isEmpty();
    }

    /** The number of broadcast lines configured. */
    public int lineCount() {
        return lines.size();
    }

    /**
     * Whether a tick should fire given {@code onlinePlayers}: there is at least one line and the online count
     * meets the minimum-players gate. A gate of zero always passes.
     */
    public boolean shouldFire(int onlinePlayers) {
        if (onlinePlayers < 0) {
            throw new IllegalArgumentException("onlinePlayers must not be negative: " + onlinePlayers);
        }
        return hasLines() && onlinePlayers >= minOnlinePlayers;
    }

    /** The line at {@code index}; callers obtain {@code index} from the cursor advanced by the use case. */
    public String lineAt(int index) {
        Objects.checkIndex(index, lines.size());
        return lines.get(index);
    }

    /**
     * Bridge this flat schedule to the richer {@link AnnouncerConfig}: an empty schedule maps to
     * {@link AnnouncerConfig#empty()}, and a schedule with lines maps to a single unconditional {@code "default"}
     * announcement carrying all of them on the {@link BroadcastChannel#CHAT} channel. This preserves the legacy
     * single-announcement behaviour while the {@code NextAnnouncement} use case selects over announcements.
     */
    public AnnouncerConfig asConfig() {
        if (!hasLines()) {
            return new AnnouncerConfig(interval, minOnlinePlayers, ordering, List.of());
        }
        Announcement single = new Announcement(
                "default",
                lines,
                DisplayCondition.always(),
                Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false);
        return new AnnouncerConfig(interval, minOnlinePlayers, ordering, List.of(single));
    }
}
