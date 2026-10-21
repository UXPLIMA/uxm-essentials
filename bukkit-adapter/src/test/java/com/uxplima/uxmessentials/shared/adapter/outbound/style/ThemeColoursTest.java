package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.kyori.adventure.text.format.TextColor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The colours a server's theme file gives every line, read the way the plugin reads them when it enables. */
class ThemeColoursTest {

    @TempDir
    Path plugins;

    @AfterEach
    void restore() {
        StyleTags.use(ThemeFile.shippedTheme());
    }

    @Test
    @DisplayName("a server with no theme file keeps the colours this plugin ships")
    void shippedColoursAreUnchanged() throws IOException {
        use("");

        assertThat(StyleTags.accent()).isEqualTo(TextColor.color(0x38b6ff));
        assertThat(StyleTags.value()).isEqualTo(TextColor.color(0x8fd9ff));
        assertThat(StyleTags.good()).isEqualTo(TextColor.color(0x5be38c));
        assertThat(StyleTags.bad()).isEqualTo(TextColor.color(0xff6b6b));
        assertThat(StyleTags.gold()).isEqualTo(TextColor.color(0xffc93c));
        assertThat(StyleTags.emerald()).isEqualTo(TextColor.color(0x45d9a6));
    }

    @Test
    @DisplayName("a role may name a colour of the palette")
    void aRoleNamesAPaletteColour() throws IOException {
        use("palette { sky = \"#48cae4\" }\nroles { accent = sky }\n");

        assertThat(StyleTags.accent()).isEqualTo(TextColor.color(0x48cae4));
    }

    @Test
    @DisplayName("a role the file leaves out keeps its shipped colour")
    void unwrittenRolesKeepTheirColour() throws IOException {
        use("roles { accent = \"#ff0000\" }\n");

        assertThat(StyleTags.accent()).isEqualTo(TextColor.color(0xff0000));
        assertThat(StyleTags.body()).isEqualTo(TextColor.color(0xffffff));
    }

    @Test
    @DisplayName("a role this plugin never heard of is still a role")
    void aServerMayInventARole() throws IOException {
        use("roles { premium = \"#b388ff\" }\n");

        assertThat(StyleTags.theme().hasColour("premium")).isTrue();
        assertThat(StyleTags.theme().colour("premium")).isEqualTo(TextColor.color(0xb388ff));
        assertThat(StyledText.render("<premium>vip").color()).isEqualTo(TextColor.color(0xb388ff));
    }

    @Test
    @DisplayName("a gradient takes its second stop from the wheel when the file names one")
    void theWheelGivesTheLighterHalfOfARamp() throws IOException {
        use("palette { mint = \"#4ecca3\", sky = \"#48cae4\" }\nroles { good = mint }\nwheel = [mint, sky]\n");

        assertThat(StyleTags.good()).isEqualTo(TextColor.color(0x4ecca3));
        assertThat(StyleTags.emerald()).isEqualTo(TextColor.color(0x48cae4));
    }

    @Test
    @DisplayName("a value that is neither a colour nor a palette name fails at load")
    void nonsenseFailsLoudly() throws IOException {
        assertThatThrownBy(() -> use("roles { accent = mint }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mint");
    }

    /** Put {@code conf} where the plugin's own theme file lives, read it, and draw with it. */
    private void use(String conf) throws IOException {
        Path dataFolder = Files.createDirectories(plugins.resolve("uxmEssentials"));
        if (!conf.isEmpty()) {
            Files.writeString(dataFolder.resolve("theme.conf"), conf, StandardCharsets.UTF_8);
        }
        StyleTags.use(ThemeFile.read(dataFolder));
    }
}
