package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.CommandCatalogConfig;
import com.uxplima.uxmessentials.shared.application.command.CommandCatalogRenderer;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.CommandOverride;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the generated default file is round-trip-compatible with the shipped loader: rendering the
 * resolved surface into {@code commands/commands.conf} and reading it back through the real
 * {@link CommandCatalogConfig} reconstructs each command's name, aliases and enabled flag. This is the
 * contract that keeps the write half ({@link CommandCatalogRenderer}) and the read half in lockstep.
 */
class CommandCatalogDefaultsTest {

    @Test
    void generatedFileLoadsBackThroughTheRealLoader(@TempDir Path dataFolder) throws Exception {
        List<EffectiveCommand> surface = List.of(
                new EffectiveCommand(new CommandId("home"), "home", List.of("h"), true, true),
                new EffectiveCommand(new CommandId("tpa"), "call", List.of("tpask", "summon"), true, false),
                new EffectiveCommand(new CommandId("spawn"), "spawn", List.of(), false, true));
        Path commands = Files.createDirectories(dataFolder.resolve("commands"));
        Files.writeString(commands.resolve("commands.conf"), CommandCatalogRenderer.render(surface));

        Map<String, CommandOverride> loaded = new CommandCatalogConfig(dataFolder, new NoopLogger()).load();

        assertThat(loaded.get("home"))
                .isEqualTo(new CommandOverride(true, Optional.of("home"), List.of("h"), Optional.of(true)));
        assertThat(loaded.get("tpa"))
                .isEqualTo(
                        new CommandOverride(true, Optional.of("call"), List.of("tpask", "summon"), Optional.of(false)));
        assertThat(loaded.get("spawn"))
                .isEqualTo(new CommandOverride(false, Optional.of("spawn"), List.of(), Optional.of(true)));
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
