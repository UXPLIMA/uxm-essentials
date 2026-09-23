package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.menu.Menus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.Mockito;

/**
 * The warp editor's particle and sound pickers offer what the warps file lists, named in the reader's language.
 *
 * <p>Until 2026-09-23 both lists were written in the two views with English names: an operator could not change a
 * picker, and a Turkish player read "Dragon Breath" and "Note Block Chime".
 */
class WarpPresetsTest {

    private static final Logger QUIET = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    @TempDir
    Path folder;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the shipped file lists fourteen particles and fourteen sounds, each named in every shipped language")
    void theShippedFileNamesEveryPresetInEveryLanguage() {
        WarpPresets shipped = WarpPresets.bundled(QUIET);
        List<String> languages = shippedLanguages();

        assertThat(shipped.particles()).hasSize(14);
        assertThat(shipped.sounds()).hasSize(14);
        assertThat(languages).contains("en", "tr").hasSizeGreaterThan(2);
        List<String> missing = new ArrayList<>();
        for (WarpPresets.Preset preset : concat(shipped.particles(), shipped.sounds())) {
            for (String language : languages) {
                if (!preset.names().containsKey(language)) {
                    missing.add(preset.effect() + " has no " + language + " name");
                }
            }
        }
        assertThat(missing).isEmpty();
        assertThat(shipped.particles().getFirst().nameIn("tr")).isEqualTo("Portal");
        assertThat(shipped.sounds().getFirst().nameIn("tr"))
                .isNotEqualTo(shipped.sounds().getFirst().nameIn("en"));
    }

    @Test
    @DisplayName("an operator's list replaces the shipped one, in its own order, and names fall back to English")
    void anOperatorsListIsWhatThePickerOffers() throws IOException {
        WarpPresets presets = WarpPresets.read(store("""
                        selector-presets {
                          particles = [
                            { effect = "minecraft:flame", icon = TORCH, name { en = "Flame", tr = "Alev" } }
                            { effect = "minecraft:note", icon = NOTE_BLOCK, name { en = "Note" } }
                            { effect = "minecraft:heart", icon = NOT_A_THING }
                            { effect = "minecraft:cloud", icon = FEATHER }
                          ]
                          sounds = []
                        }
                        """), WarpPresets.bundled(QUIET), QUIET);

        assertThat(presets.particles())
                .extracting(WarpPresets.Preset::effect)
                .containsExactly("minecraft:flame", "minecraft:note", "minecraft:cloud");
        assertThat(presets.particles().getFirst().icon()).isEqualTo(Material.TORCH);
        assertThat(presets.particles().get(0).nameIn("tr")).isEqualTo("Alev");
        assertThat(presets.particles().get(1).nameIn("tr")).isEqualTo("Note");
        assertThat(presets.particles().get(2).nameIn("tr")).isEqualTo("minecraft:cloud");
        assertThat(presets.sounds()).as("an empty list offers none").isEmpty();
    }

    @Test
    @DisplayName("a file written before the section existed offers the shipped presets")
    void anOlderFileOffersTheShippedPresets() throws IOException {
        WarpPresets presets = WarpPresets.read(store("enabled = true\n"), WarpPresets.bundled(QUIET), QUIET);

        assertThat(presets.particles()).isEqualTo(WarpPresets.bundled(QUIET).particles());
        assertThat(presets.sounds()).isEqualTo(WarpPresets.bundled(QUIET).sounds());
    }

    @Test
    @DisplayName("both pickers draw the operator's presets and name them in the viewer's language")
    void thePickersDrawTheOperatorsPresetsInTheViewersLanguage() throws IOException {
        WarpPresets presets = WarpPresets.read(store("""
                        selector-presets {
                          particles = [{ effect = "minecraft:flame", icon = TORCH, name { en = "Flame", tr = "Alev" } }]
                          sounds = [{ effect = "minecraft:block.bell.use", icon = BELL, name { en = "Bell", tr = "Çan" } }]
                        }
                        """), WarpPresets.bundled(QUIET), QUIET);
        Messages turkish = new Messages() {
            @Override
            public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
                return key == SharedMessageKey.LANG_CODE ? "tr" : key.key();
            }
        };
        Menus menus = Mockito.mock(Menus.class);
        WarpRepository repository = Mockito.mock(WarpRepository.class);
        WarpEditorView editor = Mockito.mock(WarpEditorView.class);
        TextInput input = Mockito.mock(TextInput.class);
        PlayerRef viewer = new PlayerRef(java.util.UUID.randomUUID(), "Ayse");

        WarpParticleSelectorView particles =
                WarpParticleSelectorView.create(turkish, menus, repository, editor, input, presets);
        WarpSoundSelectorView sounds = WarpSoundSelectorView.create(turkish, menus, repository, editor, input, presets);

        assertThat(particles.getOptions())
                .extracting(WarpParticleSelectorView.ParticleOption::particleName)
                .containsExactly("minecraft:flame");
        assertThat(particles.nameOf(particles.getOptions().getFirst(), viewer)).isEqualTo("Alev");
        assertThat(sounds.getOptions())
                .extracting(WarpSoundSelectorView.SoundOption::soundName)
                .containsExactly("minecraft:block.bell.use");
        assertThat(sounds.nameOf(sounds.getOptions().getFirst(), viewer)).isEqualTo("Çan");
    }

    private ConfigurateConfigStore store(String text) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return ConfigurateConfigStore.load(file, QUIET);
    }

    private static List<String> shippedLanguages() {
        List<String> languages = new ArrayList<>();
        for (String code : Locale.getISOLanguages()) {
            if (WarpPresetsTest.class.getClassLoader().getResource("messages/messages_" + code + ".conf") != null) {
                languages.add(code);
            }
        }
        return languages;
    }

    private static List<WarpPresets.Preset> concat(List<WarpPresets.Preset> one, List<WarpPresets.Preset> two) {
        List<WarpPresets.Preset> both = new ArrayList<>(one);
        both.addAll(two);
        return both;
    }
}
