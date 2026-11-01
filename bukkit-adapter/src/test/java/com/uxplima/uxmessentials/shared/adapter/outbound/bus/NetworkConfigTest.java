package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** This backend's network name is server-name, and a file that still says server-id is read the same way. */
class NetworkConfigTest {

    private static final Logger QUIET = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    @TempDir
    Path folder;

    private NetworkConfig read(String network) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "network {\n" + network + "\n}\n");
        return NetworkConfig.from(ConfigurateConfigStore.load(file, QUIET));
    }

    @Test
    void theServerNameIsRead() throws IOException {
        assertThat(read("server-name = \"survival-1\"").serverId()).isEqualTo("survival-1");
    }

    @Test
    void anOldFileThatSaysServerIdIsReadTheSameWay() throws IOException {
        assertThat(read("server-id = \"survival-2\"").serverId()).isEqualTo("survival-2");
    }

    @Test
    void theNewNameWinsWhenAFileHasBoth() throws IOException {
        assertThat(read("server-name = \"new\"\n  server-id = \"old\"").serverId())
                .isEqualTo("new");
    }
}
