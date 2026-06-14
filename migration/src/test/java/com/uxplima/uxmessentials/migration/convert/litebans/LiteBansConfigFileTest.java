package com.uxplima.uxmessentials.migration.convert.litebans;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the LiteBans H2-file fallback resolution (the no-jdbc-url path). This is the harder-to-test
 * mode, so it is exercised in isolation: the file discovery under {@code plugins/LiteBans/} and the read-only
 * URL shape, separately from the JDBC round-trip which uses the connectable-URL path.
 */
class LiteBansConfigFileTest {

    @Test
    void discoversAnMvDbFileUnderTheLiteBansFolderAndBuildsAReadOnlyUrl(@TempDir Path plugins) throws IOException {
        Path liteBans = Files.createDirectories(plugins.resolve("LiteBans"));
        Path data = Files.createFile(liteBans.resolve("litebans.mv.db"));

        LiteBansConfig config = new LiteBansConfig(Optional.empty(), "", "", "litebans_", Optional.of(plugins));

        assertThat(config.h2DataFile()).contains(data);
        String url = config.h2FileUrl().orElseThrow();
        // H2 names a file db by its path WITHOUT the .mv.db suffix, and the fallback opens it read-only.
        assertThat(url).startsWith("jdbc:h2:file:");
        assertThat(url).doesNotContain(".mv.db");
        assertThat(url).endsWith(";ACCESS_MODE_DATA=r");
    }

    @Test
    void resolvesNoFileWhenTheLiteBansFolderIsAbsent(@TempDir Path plugins) {
        LiteBansConfig config = new LiteBansConfig(Optional.empty(), "", "", "litebans_", Optional.of(plugins));

        assertThat(config.h2DataFile()).isEmpty();
        assertThat(config.connection()).isEmpty();
    }

    @Test
    void aConfiguredJdbcUrlTakesPrecedenceOverFileDiscovery(@TempDir Path plugins) throws IOException {
        Files.createDirectories(plugins.resolve("LiteBans"));
        Files.createFile(plugins.resolve("LiteBans").resolve("litebans.mv.db"));

        LiteBansConfig config = new LiteBansConfig(
                Optional.of("jdbc:mariadb://localhost:3306/litebans"),
                "root",
                "secret",
                "litebans_",
                Optional.of(plugins));

        assertThat(config.connection()).isPresent();
        assertThat(config.connection().orElseThrow().url()).isEqualTo("jdbc:mariadb://localhost:3306/litebans");
        assertThat(config.connection().orElseThrow().username()).contains("root");
    }
}
