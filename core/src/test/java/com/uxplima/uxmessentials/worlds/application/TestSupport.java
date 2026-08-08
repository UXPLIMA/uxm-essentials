package com.uxplima.uxmessentials.worlds.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

final class TestSupport {
    private TestSupport() {}

    static Notifier notifier() {
        Messages messages = (v, key, ph) -> key.key();
        MessageSink sink = (v, text) -> {};
        return new Notifier(messages, sink);
    }

    /** A {@link Notifier} that records, in order, every message key it is asked to send. */
    static final class CapturingNotifier {
        final List<MessageKey> keys = new ArrayList<>();

        Notifier notifier() {
            Messages messages = (v, key, ph) -> {
                keys.add(key);
                return key.key();
            };
            MessageSink sink = (v, text) -> {};
            return new Notifier(messages, sink);
        }
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
