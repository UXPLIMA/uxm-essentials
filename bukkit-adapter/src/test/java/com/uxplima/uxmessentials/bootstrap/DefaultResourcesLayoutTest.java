package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultResourcesLayoutTest {

    @Test
    void firstRunExtractsThePerModuleTree(@TempDir Path dir) {
        DefaultResources.writeInto(dir, Logger.getLogger("test"));
        assertThat(Files.exists(dir.resolve("config.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/teleport/config.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/teleport/rtp.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/economy/currencies.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/communication/config.conf")))
                .isTrue();
        assertThat(Files.exists(dir.resolve("messages/messages_en.conf"))).isTrue();
    }
}
