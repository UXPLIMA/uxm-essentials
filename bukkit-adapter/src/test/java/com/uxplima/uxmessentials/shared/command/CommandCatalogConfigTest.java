package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.CommandCatalogConfig;
import com.uxplima.uxmessentials.shared.application.command.CommandOverride;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the outbound adapter that reads root {@code commands.conf} into the override map the
 * {@link com.uxplima.uxmessentials.shared.application.command.CommandCatalog} resolves against. The map
 * is keyed by the raw command id string; the historical split directory remains a migration fallback.
 */
class CommandCatalogConfigTest {

    @Test
    void loadsOverridesFromMultipleFiles(@TempDir Path dataFolder) throws Exception {
        Path commands = Files.createDirectories(dataFolder.resolve("commands"));
        Files.writeString(
                commands.resolve("teleport.conf"),
                "commands { tpa { enabled=true, name=\"call\", aliases=[\"tpask\",\"summon\"] } }\n");
        Files.writeString(commands.resolve("homes.conf"), "commands { home { enabled=false } }\n");

        Map<String, CommandOverride> map = new CommandCatalogConfig(dataFolder, new NoopLogger()).load();

        assertThat(map.get("tpa"))
                .isEqualTo(
                        new CommandOverride(true, Optional.of("call"), List.of("tpask", "summon"), Optional.empty()));
        assertThat(map.get("home").enabled()).isFalse();
        assertThat(map.get("home").name()).isEmpty();
        assertThat(map.get("home").aliases()).isEmpty();
        assertThat(map.get("home").gui()).isEmpty();
    }

    @Test
    void absentDirYieldsEmptyMap(@TempDir Path dataFolder) {
        Map<String, CommandOverride> map = new CommandCatalogConfig(dataFolder, new NoopLogger()).load();

        assertThat(map).isEmpty();
    }

    @Test
    void absentFieldsFallBackToDefaults(@TempDir Path dataFolder) throws Exception {
        Files.writeString(dataFolder.resolve("commands.conf"), "commands { tpa { } }\n");

        Map<String, CommandOverride> map = new CommandCatalogConfig(dataFolder, new NoopLogger()).load();

        CommandOverride tpa = map.get("tpa");
        assertThat(tpa.enabled()).isTrue();
        assertThat(tpa.name()).isEmpty();
        assertThat(tpa.aliases()).isEmpty();
        assertThat(tpa.gui()).isEmpty();
    }

    @Test
    void readsPerCommandGuiAndGlobalDefault(@TempDir Path dataFolder) throws Exception {
        Files.writeString(
                dataFolder.resolve("commands.conf"),
                "gui-default = false\ncommands { kit { gui = true }, pay { gui = false }, msg { } }\n");

        CommandCatalogConfig.Loaded loaded = new CommandCatalogConfig(dataFolder, new NoopLogger()).loadAll();

        assertThat(loaded.guiDefault()).isFalse();
        assertThat(loaded.overrides().get("kit").gui()).contains(true);
        assertThat(loaded.overrides().get("pay").gui()).contains(false);
        assertThat(loaded.overrides().get("msg").gui()).isEmpty();
    }

    @Test
    void guiDefaultDefaultsToTrueWhenAbsent(@TempDir Path dataFolder) throws Exception {
        Files.writeString(dataFolder.resolve("commands.conf"), "commands { kit { } }\n");

        assertThat(new CommandCatalogConfig(dataFolder, new NoopLogger())
                        .loadAll()
                        .guiDefault())
                .isTrue();
    }

    @Test
    void migratesTheHistoricalSingleCatalogToTheRoot(@TempDir Path dataFolder) throws Exception {
        Path commands = Files.createDirectories(dataFolder.resolve("commands"));
        Path legacy = commands.resolve("commands.conf");
        Files.writeString(legacy, "commands { home { name=\"ev\" } }\n");

        Map<String, CommandOverride> loaded = new CommandCatalogConfig(dataFolder, new NoopLogger()).load();

        assertThat(loaded.get("home").name()).contains("ev");
        assertThat(Files.readString(dataFolder.resolve("commands.conf"))).contains("name=\"ev\"");
        assertThat(legacy).doesNotExist();
        assertThat(commands).doesNotExist();
    }

    @Test
    void rootCatalogIsAuthoritativeWhenTheLegacyDirectoryAlsoExists(@TempDir Path dataFolder) throws Exception {
        Files.writeString(dataFolder.resolve("commands.conf"), "commands { home { name=\"root\" } }\n");
        Path legacy = Files.createDirectories(dataFolder.resolve("commands"));
        Files.writeString(legacy.resolve("commands.conf"), "commands { home { name=\"legacy\" } }\n");

        Map<String, CommandOverride> loaded = new CommandCatalogConfig(dataFolder, new NoopLogger()).load();

        assertThat(loaded.get("home").name()).contains("root");
    }

    @Test
    void readsUnicodeLocalizedAliasesFromTheRootCatalog(@TempDir Path dataFolder) throws Exception {
        Files.writeString(
                dataFolder.resolve("commands.conf"),
                "commands { spawn { localized-aliases { tr=[\"doğma\",\"başlangıç\"], de=[\"startpunkt\"] } } }\n");

        CommandOverride spawn =
                new CommandCatalogConfig(dataFolder, new NoopLogger()).load().get("spawn");

        assertThat(spawn.localizedAliases().get("tr")).containsExactly("doğma", "başlangıç");
        assertThat(spawn.localizedAliases().get("de")).containsExactly("startpunkt");
    }

    @Test
    void malformedFileIsSkippedAndOthersStillLoad(@TempDir Path dataFolder) throws Exception {
        Path commands = Files.createDirectories(dataFolder.resolve("commands"));
        Files.writeString(commands.resolve("broken.conf"), "commands { tpa { aliases = [ \n");
        Files.writeString(commands.resolve("homes.conf"), "commands { home { name=\"casa\" } }\n");

        CommandCatalogConfig config = new CommandCatalogConfig(dataFolder, new NoopLogger());

        assertThatCode(config::load).doesNotThrowAnyException();
        Map<String, CommandOverride> map = config.load();
        assertThat(map.get("home").name()).contains("casa");
    }

    @Test
    void aCommandsDirectoryHoldingOnlyCustomCommandsIsNotALegacyCatalog(@TempDir Path dataFolder) throws Exception {
        // The shape of a fresh install since the customcommands module shipped: commands/ exists, but everything
        // in it belongs to that module. Warning here told operators to move files that were not there.
        Path custom = Files.createDirectories(dataFolder.resolve("commands").resolve("custom"));
        Files.writeString(custom.resolve("example.conf"), "hello { permission = \"uxmessentials.custom.hello\" }\n");
        RecordingLogger log = new RecordingLogger();

        Map<String, CommandOverride> map = new CommandCatalogConfig(dataFolder, log).load();

        assertThat(map).isEmpty();
        assertThat(log.warnings).isEmpty();
        assertThat(custom.resolve("example.conf"))
                .as("the custom command definitions are not ours to read, move or delete")
                .exists();
    }

    @Test
    void aRootCatalogBesideOnlyCustomCommandsSaysNothingAboutBeingOverridden(@TempDir Path dataFolder)
            throws Exception {
        Files.writeString(dataFolder.resolve("commands.conf"), "commands { home { enabled=false } }\n");
        Files.createDirectories(dataFolder.resolve("commands").resolve("custom"));
        RecordingLogger log = new RecordingLogger();

        Map<String, CommandOverride> map = new CommandCatalogConfig(dataFolder, log).load();

        assertThat(map.get("home").enabled()).isFalse();
        assertThat(log.warnings)
                .as("there is no competing catalog to ignore, so there is nothing to report")
                .isEmpty();
    }

    @Test
    void aRealSplitCatalogIsStillReportedAsDeprecated(@TempDir Path dataFolder) throws Exception {
        Path commands = Files.createDirectories(dataFolder.resolve("commands"));
        Files.writeString(commands.resolve("teleport.conf"), "commands { tpa { enabled=true } }\n");
        Files.writeString(commands.resolve("homes.conf"), "commands { home { enabled=false } }\n");
        RecordingLogger log = new RecordingLogger();

        Map<String, CommandOverride> map = new CommandCatalogConfig(dataFolder, log).load();

        assertThat(map).containsKeys("tpa", "home");
        assertThat(log.warnings)
                .as("an operator who really did split the catalog still needs telling")
                .anyMatch(warning -> warning.contains("deprecated"));
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

    /** Keeps the warnings so a test can assert on what an operator would actually see in the log. */
    private static final class RecordingLogger implements Logger {

        private final List<String> warnings = new java.util.ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
