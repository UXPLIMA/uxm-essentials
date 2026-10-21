package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.kyori.adventure.text.format.TextColor;

import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One theme for the server, and what this plugin's own file may still say on top of it. */
class ThemeFileTest {

    @Test
    @DisplayName("the shared file sits beside the plugins that read it")
    void theSharedFileSitsBesideThePlugins(@TempDir Path root) {
        Path dataFolder = root.resolve("plugins").resolve("uxmEssentials");

        assertThat(ThemeFile.shared(dataFolder))
                .isEqualTo(root.resolve("plugins").resolve("uxmTheme").resolve("theme.conf"));
    }

    @Test
    @DisplayName("neither file means the shipped colours")
    void noFileMeansTheShippedColours(@TempDir Path root) throws IOException {
        Path dataFolder = Files.createDirectories(root.resolve("plugins").resolve("uxmEssentials"));

        assertThat(ThemeFile.read(dataFolder).colour("accent")).isEqualTo(TextColor.color(0x38b6ff));
    }

    @Test
    @DisplayName("the shared file is read when this plugin has none of its own")
    void theSharedFileIsRead(@TempDir Path root) throws IOException {
        Path dataFolder = Files.createDirectories(root.resolve("plugins").resolve("uxmEssentials"));
        Path shared = Files.createDirectories(root.resolve("plugins").resolve("uxmTheme"));
        Files.writeString(shared.resolve("theme.conf"), "palette { sky = \"#48cae4\" }\nroles { accent = sky }\n");

        assertThat(ThemeFile.read(dataFolder).colour("accent")).isEqualTo(TextColor.color(0x48cae4));
    }

    @Test
    @DisplayName("this plugin's own file wins key by key")
    void theOwnFileWinsKeyByKey(@TempDir Path root) throws IOException {
        Path dataFolder = Files.createDirectories(root.resolve("plugins").resolve("uxmEssentials"));
        Path shared = Files.createDirectories(root.resolve("plugins").resolve("uxmTheme"));
        Files.writeString(shared.resolve("theme.conf"), "roles { accent = \"#48cae4\", value = \"#ffe66d\" }\n");
        Files.writeString(dataFolder.resolve("theme.conf"), "roles { accent = \"#ff0000\" }\n");

        Theme theme = ThemeFile.read(dataFolder);

        assertThat(theme.colour("accent")).isEqualTo(TextColor.color(0xff0000));
        assertThat(theme.colour("value")).isEqualTo(TextColor.color(0xffe66d));
    }
}
