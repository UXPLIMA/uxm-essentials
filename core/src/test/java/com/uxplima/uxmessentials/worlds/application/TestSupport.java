package com.uxplima.uxmessentials.worlds.application;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

final class TestSupport {
    private TestSupport() {}

    static WorldNotifier notifier() {
        Messages messages = (v, key, ph) -> key.key();
        MessageSink sink = (v, text) -> {};
        return new WorldNotifier(messages, sink);
    }

    /** Runs every scheduled task inline so a use case's off-tick write tail executes before the call returns. */
    static Scheduler inlineScheduler() {
        return new InlineScheduler();
    }

    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
