package com.uxplima.uxmessentials.testing;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Wraps a scheduler so that, once {@link #hold} is called, region and entity hops wait until the test runs them, as on
 * Folia, where each hop is a task of its own. What a held task throws stops here, as a server scheduler only logs it,
 * instead of reaching the code that scheduled it. Everything else goes straight to the wrapped scheduler, which also
 * decides, when a held hop runs, whether it takes its retired branch.
 */
public final class HeldHops implements Scheduler {

    private final Scheduler delegate;
    private final Deque<Runnable> held = new ArrayDeque<>();
    private final List<RuntimeException> thrown = new ArrayList<>();
    private boolean holding;

    public HeldHops(Scheduler delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** Hold every region and entity hop from now on. */
    public void hold() {
        holding = true;
    }

    /** Run the oldest held hop, which may hold another. */
    public void runNext() {
        Runnable next = held.pollFirst();
        if (next == null) {
            throw new IllegalStateException("no hop is held");
        }
        try {
            next.run();
        } catch (RuntimeException failure) {
            thrown.add(failure);
        }
    }

    /** Run held hops, and the hops they hold, until none are left. */
    public void runAll() {
        while (!held.isEmpty()) {
            runNext();
        }
    }

    /** What the held hops threw, in order: what a server scheduler would have logged. */
    public List<RuntimeException> thrown() {
        return List.copyOf(thrown);
    }

    @Override
    public void onGlobal(Runnable task) {
        delegate.onGlobal(task);
    }

    @Override
    public void onRegion(Position position, Runnable task) {
        later(() -> delegate.onRegion(position, task));
    }

    @Override
    public void onRegion(Position position, Runnable task, Runnable retired) {
        later(() -> delegate.onRegion(position, task, retired));
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task) {
        later(() -> delegate.onEntity(player, task));
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
        later(() -> delegate.onEntity(player, task, retired));
    }

    @Override
    public void async(Runnable task) {
        delegate.async(task);
    }

    @Override
    public void asyncAfter(Duration delay, Runnable task) {
        delegate.asyncAfter(delay, task);
    }

    private void later(Runnable hop) {
        if (holding) {
            held.add(hop);
        } else {
            hop.run();
        }
    }
}
