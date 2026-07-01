package com.uxplima.uxmessentials.bootstrap.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.event.Listener;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * The per-module load loop must be fault-isolated: a module that throws while wiring is rolled back to the
 * checkpoint taken before it ran and skipped, and its siblings load unaffected. Exercised through the small
 * {@code loadModulesIsolated} seam so the isolation policy is testable apart from the full wiring arg list.
 */
class PluginModuleIsolationTest {

    @Test
    void oneFailingModuleIsRolledBackAndSkippedWhileSiblingsLoad() {
        RecordingLogger logger = new RecordingLogger();
        CloseableResources resources = new CloseableResources(logger.logger());
        FeatureModule first = fakeModule("first");
        FeatureModule bad = fakeModule("bad");
        FeatureModule third = fakeModule("third");
        List<FeatureModule> loaded = new ArrayList<>();

        PluginModule.ModuleLoader loader = module -> {
            loaded.add(module);
            // Every module registers a command and a listener; the bad one throws mid-wire, after registering,
            // so its partial registrations are exactly what the rollback must drop.
            resources.addCommand(new FakeCommand(module.id().value()));
            resources.addListener(new FakeListener());
            if (module == bad) {
                throw new IllegalStateException("wire boom");
            }
        };

        assertThatCode(() -> PluginModule.loadModulesIsolated(
                        List.of(first, bad, third), resources, logger.logger(), loader))
                .doesNotThrowAnyException();

        assertThat(loaded).containsExactly(first, bad, third);
        assertThat(resources.commands()).hasSize(2);
        assertThat(resources.listeners()).hasSize(2);
        assertThat(logger.severeCount()).isEqualTo(1);
    }

    private static FeatureModule fakeModule(String id) {
        FeatureModule module = mock(FeatureModule.class);
        when(module.id()).thenReturn(ModuleId.of(id));
        return module;
    }

    private record FakeCommand(String id) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            throw new UnsupportedOperationException("build is not exercised by the isolation test");
        }

        @Override
        public String description() {
            return id;
        }
    }

    private static final class FakeListener implements Listener {}

    /** A silent {@link Logger} that only counts the SEVERE records the skip log emits. */
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
