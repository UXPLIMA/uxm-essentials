package com.uxplima.uxmessentials.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;

class DiscordConfigLoaderTest {

    @Test
    void firstLoadExtractsDormantDefault(@TempDir Path dataFolder) throws ConfigurateException {
        DiscordConfig config = DiscordConfigLoader.load(dataFolder);

        assertThat(Files.exists(dataFolder.resolve("config").resolve("discord.conf")))
                .isTrue();
        assertThat(config.enabled()).isFalse();
        assertThat(config.token()).isEmpty();
        assertThat(config.shouldConnect()).isFalse();
        assertThat(config.maxPerMinute()).isEqualTo(60);
    }

    @Test
    void editedConfigParsesTokenAndChannels(@TempDir Path dataFolder) throws Exception {
        Path file = dataFolder.resolve("config").resolve("discord.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                """
                enabled = true
                token = "secret"
                min-eco-notify = 250
                channels {
                  audit = "111"
                  economy = "222"
                }
                """);

        DiscordConfig config = DiscordConfigLoader.load(dataFolder);

        assertThat(config.enabled()).isTrue();
        assertThat(config.token()).isEqualTo("secret");
        assertThat(config.minEcoNotify()).isEqualTo(250L);
        assertThat(config.channelFor(EventCategory.AUDIT)).contains("111");
        assertThat(config.channelFor(EventCategory.ECONOMY)).contains("222");
        assertThat(config.shouldConnect()).isTrue();
    }

    @Test
    void blankChannelIdIsNotMapped(@TempDir Path dataFolder) throws Exception {
        Path file = dataFolder.resolve("config").resolve("discord.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                """
                enabled = true
                token = "secret"
                channels {
                  audit = "111"
                  economy = ""
                }
                """);

        DiscordConfig config = DiscordConfigLoader.load(dataFolder);

        assertThat(config.channelFor(EventCategory.AUDIT)).contains("111");
        assertThat(config.channelFor(EventCategory.ECONOMY)).isEmpty();
        assertThat(config.channels()).hasSize(1);
    }
}
