package com.uxplima.uxmessentials.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;

class RestConfigLoaderTest {

    @Test
    void firstLoadExtractsTheShippedDefaultAndItIsOff(@TempDir Path folder) throws ConfigurateException {
        RestConfig config = RestConfigLoader.load(folder);

        assertThat(Files.exists(folder.resolve("config").resolve("rest.conf"))).isTrue();
        assertThat(config.enabled()).isFalse();
        assertThat(config.bind()).isEqualTo("127.0.0.1");
        assertThat(config.port()).isEqualTo(8123);
        assertThat(config.requestsPerMinute()).isEqualTo(120);
    }

    @Test
    void anEditedFileIsRead(@TempDir Path folder) throws Exception {
        write(folder, """
                enabled = true
                bind = "0.0.0.0"
                port = 9000
                requests-per-minute = 30
                """);

        RestConfig config = RestConfigLoader.load(folder);

        assertThat(config.enabled()).isTrue();
        assertThat(config.bind()).isEqualTo("0.0.0.0");
        assertThat(config.port()).isEqualTo(9000);
        assertThat(config.requestsPerMinute()).isEqualTo(30);
        assertThat(config.isExposed()).isTrue();
    }

    @Test
    void aPortNobodyCanBindLeavesTheListenerOff(@TempDir Path folder) throws Exception {
        write(folder, """
                enabled = true
                port = 70000
                """);

        assertThat(RestConfigLoader.load(folder)).isEqualTo(RestConfig.DORMANT);
    }

    @Test
    void aLimitOfNothingLeavesTheListenerOff(@TempDir Path folder) throws Exception {
        write(folder, """
                enabled = true
                requests-per-minute = 0
                """);

        assertThat(RestConfigLoader.load(folder)).isEqualTo(RestConfig.DORMANT);
    }

    @Test
    void theLoopbackAddressIsNotCountedAsExposed() {
        assertThat(RestConfig.DORMANT.isExposed()).isFalse();
        assertThat(new RestConfig(true, "localhost", 8123, 120).isExposed()).isFalse();
        assertThat(new RestConfig(true, "::1", 8123, 120).isExposed()).isFalse();
    }

    @Test
    void anExtractedFileIsNotOverwrittenBySomethingTheOperatorEdited(@TempDir Path folder) throws Exception {
        RestConfigLoader.load(folder);
        Path file = folder.resolve("config").resolve("rest.conf");
        Files.writeString(file, "enabled = true\n");

        assertThat(RestConfigLoader.load(folder).enabled()).isTrue();
    }

    private static void write(Path folder, String contents) throws Exception {
        Path file = folder.resolve("config").resolve("rest.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
