package com.uxplima.uxmessentials.bootstrap.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.uxplima.uxmessentials.nametags.adapter.NametagSettings;
import com.uxplima.uxmessentials.scoreboard.adapter.ScoreboardSettings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.tablist.adapter.TablistSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards the last-known-good snapshot invariant shared by the three live HUD modules. */
class HudSettingsReloadTest {

    @Test
    void tablistRejectsMalformedReloadWithoutPublishingInertContent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.conf"), "refresh-ticks = 40\nformats { default { condition = \"\" } }\n");
        TablistSettings settings = new TablistSettings(dir, mock(Logger.class));

        Files.writeString(dir.resolve("config.conf"), "formats { broken = [\n");

        assertThatThrownBy(settings::reload).isInstanceOf(IllegalStateException.class);
        assertThat(settings.refreshInterval()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void scoreboardRejectsMalformedReloadWithoutPublishingInertContent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.conf"), """
                refresh-ticks = 40
                boards {
                  default {
                    condition = ""
                    title = "T"
                    lines = []
                  }
                }
                """);
        ScoreboardSettings settings = new ScoreboardSettings(dir, mock(Logger.class));

        Files.writeString(dir.resolve("config.conf"), "boards { broken = [\n");

        assertThatThrownBy(settings::reload).isInstanceOf(IllegalStateException.class);
        assertThat(settings.refreshInterval()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void nametagsRejectMalformedReloadWithoutPublishingInertContent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.conf"), "refresh-ticks = 40\n");
        NametagSettings settings = new NametagSettings(dir, mock(Logger.class));

        Files.writeString(dir.resolve("config.conf"), "formats { broken = [\n");

        assertThatThrownBy(settings::reload).isInstanceOf(IllegalStateException.class);
        assertThat(settings.refreshInterval()).isEqualTo(Duration.ofSeconds(2));
    }
}
