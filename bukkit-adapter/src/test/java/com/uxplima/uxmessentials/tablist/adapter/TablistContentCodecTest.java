package com.uxplima.uxmessentials.tablist.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class TablistContentCodecTest {

    @Test
    void parsesTheFullAuthoredShape(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                enabled = true
                tablist {
                  header = [ "<gold>Welcome", "<gray>%player_name%" ]
                  footer = [ "<gray>play.example.net" ]
                  refresh-ticks = 40
                  world-blacklist = [ "world_the_end", "world_nether" ]
                }
                """);

        TablistContent content = TablistContentCodec.read(root);

        assertThat(content.header()).containsExactly("<gold>Welcome", "<gray>%player_name%");
        assertThat(content.footer()).containsExactly("<gray>play.example.net");
        // 40 ticks at 50ms each is two seconds.
        assertThat(content.refreshInterval()).isEqualTo(Duration.ofSeconds(2L));
        assertThat(content.worldBlacklist()).containsExactlyInAnyOrder("world_the_end", "world_nether");
    }

    @Test
    void anEmptyRootYieldsInertContent(@TempDir Path dir) throws Exception {
        TablistContent content = TablistContentCodec.read(load(dir, "enabled = false\n"));

        assertThat(content.isBlank()).isTrue();
        assertThat(content.refreshInterval()).isPositive();
    }

    @Test
    void defaultsApplyWhenTheTablistBlockIsPartial(@TempDir Path dir) throws Exception {
        // Only a header authored: the footer defaults to empty, and a non-positive refresh-ticks falls back to one
        // second (20 ticks) rather than busy-spinning.
        ConfigurationNode root = load(
                dir,
                """
                tablist {
                  header = [ "<gold>Welcome" ]
                  refresh-ticks = 0
                }
                """);

        TablistContent content = TablistContentCodec.read(root);

        assertThat(content.header()).containsExactly("<gold>Welcome");
        assertThat(content.footer()).isEmpty();
        assertThat(content.refreshInterval()).isEqualTo(Duration.ofSeconds(1L));
        assertThat(content.worldBlacklist()).isEmpty();
    }

    private static ConfigurationNode load(Path dir, String hocon) throws ConfigurateException, IOException {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, hocon);
        return HoconConfigurationLoader.builder().path(file).build().load();
    }
}
