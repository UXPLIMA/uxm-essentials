package com.uxplima.uxmessentials.scoreboard.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class ScoreboardContentCodecTest {

    @Test
    void parsesTheFullAuthoredShape(@TempDir Path dir) throws Exception {
        // The tablist header/footer is a separate module now (modules/tablist/config.conf); a tablist block here is
        // simply ignored — only the scoreboard sidebar block is parsed.
        ConfigurationNode root = load(
                dir,
                """
                enabled = true
                scoreboard {
                  title = "<gold>My Server"
                  lines = [ "<gray>Online: <white>%server_online%", "" ]
                  refresh-ticks = 40
                  world-blacklist = [ "world_the_end", "world_nether" ]
                }
                tablist {
                  header = [ "<gold>Welcome" ]
                  footer = [ "<gray>play.example.net" ]
                }
                """);

        DisplayContent content = ScoreboardContentCodec.read(root);

        assertThat(content.title()).contains("<gold>My Server");
        assertThat(content.lines()).containsExactly("<gray>Online: <white>%server_online%", "");
        // 40 ticks at 50ms each is two seconds.
        assertThat(content.refreshInterval()).isEqualTo(Duration.ofSeconds(2L));
        assertThat(content.worldBlacklist()).containsExactlyInAnyOrder("world_the_end", "world_nether");
    }

    @Test
    void anEmptyRootYieldsInertContent(@TempDir Path dir) throws Exception {
        DisplayContent content = ScoreboardContentCodec.read(load(dir, "enabled = false\n"));

        assertThat(content.isBlank()).isTrue();
        assertThat(content.refreshInterval()).isPositive();
    }

    @Test
    void aBlankTitleIsAbsentAndTooManyLinesAreTruncated(@TempDir Path dir) throws Exception {
        StringBuilder lines = new StringBuilder("lines = [ ");
        for (int i = 0; i < DisplayContent.MAX_LINES + 5; i++) {
            lines.append("\"line ").append(i).append("\", ");
        }
        lines.append("]");
        ConfigurationNode root = load(dir, "scoreboard {\n  title = \"\"\n  " + lines + "\n  refresh-ticks = 0\n}\n");

        DisplayContent content = ScoreboardContentCodec.read(root);

        assertThat(content.title()).isEmpty();
        assertThat(content.lines()).hasSize(DisplayContent.MAX_LINES);
        // A non-positive refresh-ticks falls back to one second (20 ticks) rather than busy-spinning.
        assertThat(content.refreshInterval()).isEqualTo(Duration.ofSeconds(1L));
    }

    private static ConfigurationNode load(Path dir, String hocon) throws ConfigurateException, IOException {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, hocon);
        return HoconConfigurationLoader.builder().path(file).build().load();
    }
}
