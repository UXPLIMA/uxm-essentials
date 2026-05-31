package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.application.port.RandomSource;
import com.uxplima.uxmessentials.communication.domain.AnnouncerCursor;
import com.uxplima.uxmessentials.communication.domain.AnnouncerSchedule;

/**
 * Picks the next announcer line each timer tick, honouring the schedule's min-players gate and the
 * no-immediate-repeat rule. The adapter's {@code Scheduler}-port timer calls {@link #pick(int)} with the current
 * online count; this use case reads the live {@link AnnouncerSchedule} from the supplied holder (so a reload that
 * swaps the schedule takes effect on the next tick), checks the gate, advances the {@link AnnouncerCursor}, and
 * returns the raw line to broadcast — or empty when the tick is skipped.
 *
 * <p>The cursor is the only mutable state: it is held in an {@code AtomicReference} and advanced with {@code
 * updateAndGet} so two ticks can never read the same position, keeping the no-repeat rule honest under the
 * fire-and-forget scheduler. Randomness flows through the injected {@link RandomSource}, leaving the domain
 * cursor pure. The returned string is an operator-authored line for the adapter to render through MiniMessage —
 * never a {@code MessageKey}.
 */
public final class NextAnnouncement {

    private final Supplier<AnnouncerSchedule> schedule;
    private final RandomSource random;
    private final AtomicReference<AnnouncerCursor> cursor = new AtomicReference<>(AnnouncerCursor.start());

    public NextAnnouncement(Supplier<AnnouncerSchedule> schedule, RandomSource random) {
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * The line to broadcast this tick given {@code onlinePlayers}, or empty when the schedule has no lines or the
     * online count is below the min-players gate. Advances the rotation cursor only when a line is actually
     * chosen, so a skipped tick does not consume a position. The timer is a single serial task on the scheduler
     * port, so the read-advance-store sequence on the cursor reference is never interleaved with itself.
     */
    public Optional<String> pick(int onlinePlayers) {
        AnnouncerSchedule current = schedule.get();
        if (!current.shouldFire(onlinePlayers)) {
            return Optional.empty();
        }
        AnnouncerCursor position = Objects.requireNonNull(cursor.get(), "cursor");
        AnnouncerCursor.Selection selection =
                position.advance(current.lineCount(), current.ordering(), random::nextBounded);
        cursor.set(selection.cursor());
        return Optional.of(current.lineAt(selection.index()));
    }

    /** Reset the rotation to its starting position; called when a reload swaps a new schedule in. */
    public void reset() {
        cursor.set(AnnouncerCursor.start());
    }
}
