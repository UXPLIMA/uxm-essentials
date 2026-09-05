package com.uxplima.uxmessentials.shared.menu.colour;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the colour picker does with a layout file an operator has got wrong. Every bad value here used to reach
 * the window instead of the log: a slot that is not a number reads as zero through Configurate and zero is a
 * real slot, so the picker opened with the palette collapsed onto one square and looked deliberate. The rows
 * key and the material keys already refused and said so; these are the ones that did not.
 */
class ColourPickerLayoutTest {

    @TempDir
    Path dataFolder;

    @Test
    void aLayoutWithNoFileOnDiskFallsBackToTheBundledOne() {
        RecordingLogger log = new RecordingLogger();

        ColourPickerLayout layout = ColourPickerLayout.load(dataFolder, log);

        assertThat(layout.paletteSlots()).hasSize(16);
        assertThat(log.warnings).isEmpty();
    }

    @Test
    void aPaletteSlotThatIsNotANumberIsRefusedRatherThanReadAsZero() throws IOException {
        write("rows = 6\npalette-slots = [\"left\", \"middle\"]\n");
        RecordingLogger log = new RecordingLogger();

        ColourPickerLayout layout = ColourPickerLayout.load(dataFolder, log);

        assertThat(layout.paletteSlots())
                .isEqualTo(ColourPickerLayout.codeDefault().paletteSlots());
        assertThat(log.warnings).hasSize(1);
        assertThat(log.warnings.get(0)).contains("left").doesNotContain("-2147483648");
    }

    @Test
    void aPaletteSlotPastTheEndOfTheWindowIsRefused() throws IOException {
        // Six rows hold 54 slots, so 60 names nothing the window can draw. It used to be kept and never drawn.
        write("rows = 6\npalette-slots = [0, 60]\n");
        RecordingLogger log = new RecordingLogger();

        ColourPickerLayout layout = ColourPickerLayout.load(dataFolder, log);

        assertThat(layout.paletteSlots())
                .isEqualTo(ColourPickerLayout.codeDefault().paletteSlots());
        assertThat(log.warnings).hasSize(1);
    }

    @Test
    void aNegativeButtonSlotFallsBackInsteadOfBecomingSlotZero() throws IOException {
        // Math.max(0, ...) turned this into slot zero, where it collided with whatever the palette had drawn.
        // The warning must name the -1 the operator wrote, not the sentinel the read fell back to.
        write("rows = 6\ncustom-slot = -1\n");
        RecordingLogger log = new RecordingLogger();

        ColourPickerLayout layout = ColourPickerLayout.load(dataFolder, log);

        assertThat(layout.customSlot())
                .isEqualTo(ColourPickerLayout.codeDefault().customSlot());
        assertThat(log.warnings).hasSize(1);
        assertThat(log.warnings.get(0)).contains("-1").doesNotContain("-2147483648");
    }

    @Test
    void aButtonSlotThatIsNotANumberIsRefusedAndNamedInTheWarning() throws IOException {
        write("rows = 6\ncustom-slot = \"left\"\n");
        RecordingLogger log = new RecordingLogger();

        ColourPickerLayout layout = ColourPickerLayout.load(dataFolder, log);

        assertThat(layout.customSlot())
                .isEqualTo(ColourPickerLayout.codeDefault().customSlot());
        assertThat(log.warnings).hasSize(1);
        assertThat(log.warnings.get(0)).contains("left").doesNotContain("-2147483648");
    }

    @Test
    void anOmittedButtonSlotTakesTheBuiltInOneWithoutWarning() throws IOException {
        // An absent key and an unreadable one both read as the sentinel, so the absent case is excluded before
        // the read. Collapse that one too and every operator who leaves an optional key out gets warned at.
        write("rows = 6\n");
        RecordingLogger log = new RecordingLogger();

        ColourPickerLayout layout = ColourPickerLayout.load(dataFolder, log);

        assertThat(layout.customSlot())
                .isEqualTo(ColourPickerLayout.codeDefault().customSlot());
        assertThat(log.warnings).isEmpty();
    }

    @Test
    void aButtonSlotPastTheEndOfTheWindowFallsBack() throws IOException {
        write("rows = 6\nback-slot = 60\n");
        RecordingLogger log = new RecordingLogger();

        ColourPickerLayout layout = ColourPickerLayout.load(dataFolder, log);

        assertThat(layout.backSlot()).isEqualTo(ColourPickerLayout.codeDefault().backSlot());
        assertThat(log.warnings).hasSize(1);
    }

    @Test
    void aLayoutThatIsEntirelyValidIsTakenAsWritten() throws IOException {
        write("rows = 6\npalette-slots = [1, 2]\ncustom-slot = 3\n");
        RecordingLogger log = new RecordingLogger();

        ColourPickerLayout layout = ColourPickerLayout.load(dataFolder, log);

        assertThat(layout.paletteSlots()).containsExactly(1, 2);
        assertThat(layout.customSlot()).isEqualTo(3);
        assertThat(log.warnings).isEmpty();
    }

    private void write(String body) throws IOException {
        Path file = dataFolder
                .resolve("modules")
                .resolve(ColourPickerLayout.MODULE)
                .resolve("gui")
                .resolve(ColourPickerLayout.NAME + ".conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
    }

    /**
     * Collects the warnings, arguments included, so a test can assert the picker named the value the operator
     * typed. Reading a node with a default can copy that default into the node, and a warning that reports the
     * node afterwards then prints the sentinel instead of the word, telling nobody anything.
     */
    private static final class RecordingLogger implements Logger {

        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void debug(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message + " " + Arrays.toString(args));
        }

        @Override
        public void error(String message, Throwable cause) {}
    }
}
