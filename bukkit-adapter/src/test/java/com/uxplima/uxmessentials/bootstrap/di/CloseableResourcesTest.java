package com.uxplima.uxmessentials.bootstrap.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.event.Listener;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.junit.jupiter.api.Test;

/**
 * Teardown and rollback must each be fault-isolated: one throwing stop-hook cannot abandon the rest of the
 * chain (the DB pool hook is pushed first and popped last, so it has to run whatever an earlier module does),
 * and rolling a half-started module back must undo exactly what it registered without aborting on a throw.
 */
class CloseableResourcesTest {

    @Test
    void closeRunsEveryHookEvenWhenOneThrows() {
        RecordingLogger logger = new RecordingLogger();
        CloseableResources resources = new CloseableResources(logger.logger());
        AtomicInteger first = new AtomicInteger();
        AtomicInteger third = new AtomicInteger();
        // Pushed in order, popped LIFO: the throwing middle hook must not skip the hooks on either side of it.
        resources.onClose(first::incrementAndGet);
        resources.onClose(() -> {
            throw new IllegalStateException("stop boom");
        });
        resources.onClose(third::incrementAndGet);

        resources.close();

        assertThat(first).hasValue(1);
        assertThat(third).hasValue(1);
        assertThat(logger.severeCount()).isEqualTo(1);
    }

    @Test
    void rollbackUndoesRegistrationsSinceScopeAndRunsTheScopedHooks() {
        RecordingLogger logger = new RecordingLogger();
        CloseableResources resources = new CloseableResources(logger.logger());
        // A registration that predates the scope: it belongs to a sibling module and must survive the rollback.
        resources.addCommand(new FakeCommand("base"));
        resources.addListener(new FakeListener());

        CloseableResources.Scope scope = resources.openScope();
        AtomicInteger scopedHook = new AtomicInteger();
        resources.addCommand(new FakeCommand("partial"));
        resources.addListener(new FakeListener());
        resources.onClose(scopedHook::incrementAndGet);

        resources.rollbackTo(scope);

        assertThat(scopedHook).hasValue(1);
        assertThat(resources.commands()).hasSize(1);
        assertThat(resources.listeners()).hasSize(1);
    }

    @Test
    void rollbackContinuesWhenAScopedHookThrows() {
        RecordingLogger logger = new RecordingLogger();
        CloseableResources resources = new CloseableResources(logger.logger());
        CloseableResources.Scope scope = resources.openScope();
        AtomicInteger survivor = new AtomicInteger();
        resources.addCommand(new FakeCommand("partial"));
        // Hooks pop newest-first: the survivor is pushed first (popped last), the thrower last (popped first).
        resources.onClose(survivor::incrementAndGet);
        resources.onClose(() -> {
            throw new IllegalStateException("rollback boom");
        });

        resources.rollbackTo(scope);

        assertThat(survivor).hasValue(1);
        assertThat(resources.commands()).isEmpty();
        assertThat(logger.severeCount()).isEqualTo(1);
    }

    private record FakeCommand(String id) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            throw new UnsupportedOperationException("build is not exercised by scope/teardown tests");
        }

        @Override
        public String description() {
            return id;
        }
    }

    private static final class FakeListener implements Listener {}

    /** A silent {@link Logger} that only counts the SEVERE records the guards emit. */
    private static final class RecordingLogger extends Handler {
        private final Logger logger = Logger.getAnonymousLogger();
        private int severe;

        RecordingLogger() {
            logger.setUseParentHandlers(false);
            logger.addHandler(this);
        }

        Logger logger() {
            return logger;
        }

        int severeCount() {
            return severe;
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().equals(Level.SEVERE)) {
                severe++;
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
