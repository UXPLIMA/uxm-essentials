package com.uxplima.uxmessentials.shared.adapter.inbound.window;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Windows asked for that have not opened yet, each with what to run once it does. A window that opens later than it
 * was asked for (a menu built off the tick) can only be confirmed from its open event, and only when nothing
 * cancelled it. A window that is not seen within its wait ({@link #WAIT} unless given) is forgotten, so a later open
 * of the same window confirms nothing.
 *
 * @param <W> the window type
 */
@NullMarked
public final class OpenWatch<W> {

    /** How long a window may take to open, unless the watch is given another wait, before nothing is confirmed. */
    public static final Duration WAIT = Duration.ofSeconds(10);

    private record Expected<W>(Predicate<W> window, Runnable shown, long deadline) {}

    private final Map<UUID, List<Expected<W>>> expected = new ConcurrentHashMap<>();
    private final LongSupplier nanos;
    private final Duration wait;

    /** @param nanos a monotonic clock in nanoseconds */
    public OpenWatch(LongSupplier nanos) {
        this(nanos, WAIT);
    }

    /**
     * @param nanos a monotonic clock in nanoseconds
     * @param wait how long a window may take to open
     */
    public OpenWatch(LongSupplier nanos, Duration wait) {
        this.nanos = Objects.requireNonNull(nanos, "nanos");
        this.wait = Objects.requireNonNull(wait, "wait");
    }

    /** Run {@code shown} once a window matching {@code window} opens for {@code player} within the wait. */
    public void expect(UUID player, Predicate<W> window, Runnable shown) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(shown, "shown");
        Expected<W> entry = new Expected<>(window, shown, nanos.getAsLong() + wait.toNanos());
        expected.compute(player, (uuid, list) -> {
            List<Expected<W>> next = live(list);
            next.add(entry);
            return next;
        });
    }

    /**
     * An open of {@code window} for {@code player} was decided. The first matching entry is used up either way, and
     * runs only when the open went through.
     */
    public void decided(UUID player, W window, boolean cancelled) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(window, "window");
        List<Runnable> run = new ArrayList<>(1);
        expected.computeIfPresent(player, (uuid, list) -> {
            List<Expected<W>> next = live(list);
            for (int i = 0; i < next.size(); i++) {
                Expected<W> entry = next.get(i);
                if (entry.window().test(window)) {
                    next.remove(i);
                    if (!cancelled) {
                        run.add(entry.shown());
                    }
                    break;
                }
            }
            return next.isEmpty() ? null : next;
        });
        run.forEach(Runnable::run);
    }

    /** Drop everything expected for {@code player}, who left. */
    public void forget(UUID player) {
        expected.remove(Objects.requireNonNull(player, "player"));
    }

    private List<Expected<W>> live(@Nullable List<Expected<W>> list) {
        List<Expected<W>> next = new ArrayList<>();
        if (list == null) {
            return next;
        }
        long now = nanos.getAsLong();
        for (Expected<W> entry : list) {
            if (now - entry.deadline() < 0) {
                next.add(entry);
            }
        }
        return next;
    }
}
