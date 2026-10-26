package com.uxplima.uxmessentials.bootstrap.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The menu engine plays a click, a page turn and a refusal only when the plugin hands it sounds. uxmLib has no
 * tones of its own, so a listener built without them is silent, and an operator could never reach the ones it
 * used to play.
 */
final class ShippedMenuSoundsTest {

    @Test
    @DisplayName("the shipped config writes out the click, page and refusal sounds")
    void theShippedConfigNamesTheMenuSounds() {
        HoconConfig config = HoconConfig.load(Path.of("src/main/resources/config.conf"));

        assertThat(config.getString("menu.sounds.click.name", "")).isEqualTo("block.note_block.pling");
        assertThat(config.getString("menu.sounds.page.name", "")).isEqualTo("item.book.page_turn");
        assertThat(config.getString("menu.sounds.denied.name", "")).isEqualTo("block.note_block.bass");
    }

    @Test
    @DisplayName("the menu listener plays the sounds that file names, and a reload reads them again")
    void theListenerIsHandedTheFile() throws IOException {
        String module =
                Files.readString(Path.of("src/main/java/com/uxplima/uxmessentials/bootstrap/di/PluginModule.java"));

        assertThat(module)
                .contains("MenuSoundsFile.read(")
                .contains(".feedback(menuSounds)")
                .contains("menuSounds::reload");
    }
}
