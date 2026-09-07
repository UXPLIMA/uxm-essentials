// This test sits in uxmLib's package on purpose, and it is not a mistake to tidy away.
// AnvilTextBackend, ChatTextBackend, DialogTextBackend and TextInputBackend are package-private in
// com.uxplima.uxmlib.gui.input, so the only way to drive them is from the mirror package. It stays in
// uxmEssentials because what it covers is this plugin's wiring of the input seam, not the library's
// backends in isolation. Widening the library to reach it would be the wrong trade.
package com.uxplima.uxmlib.gui.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.shared.adapter.outbound.EngineLog;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the operator config of the text-input seam: the global default, the per-key overrides, the cancel-keyword list,
 * and the inert defaults when the file is absent or partial. These are the knobs an operator flips to put any one input
 * point on anvil or chat and to alias the cancel word.
 */
class InputSettingsTest {

    @TempDir
    Path dir;

    @Test
    void absentFileFallsBackToAnvilDefaultAndTheTwoCancelKeywords() {
        InputSettings settings = new InputSettings(dir.resolve("text-input.conf"), EngineLog.of(new SilentLogger()));

        assertThat(settings.modeFor("anything")).isEqualTo(InputMode.ANVIL);
        assertThat(settings.cancelKeywords()).containsExactly("cancel", "iptal");
        assertThat(settings.isCancel("CANCEL")).isTrue();
        assertThat(settings.isCancel(" iptal ")).isTrue();
        assertThat(settings.isCancel("nope")).isFalse();
    }

    @Test
    void perKeyOverrideWinsOverTheGlobalDefault() throws IOException {
        write("""
                default-mode = chat
                modes {
                  "home.rename" = anvil
                  "loan.amount" = chat
                }
                cancel-keywords = ["stop", "dur"]
                """);
        InputSettings settings = new InputSettings(dir.resolve("text-input.conf"), EngineLog.of(new SilentLogger()));

        assertThat(settings.modeFor("home.rename")).isEqualTo(InputMode.ANVIL);
        assertThat(settings.modeFor("loan.amount")).isEqualTo(InputMode.CHAT);
        // a key with no override takes the global default
        assertThat(settings.modeFor("warp.password")).isEqualTo(InputMode.CHAT);
        assertThat(settings.cancelKeywords()).containsExactly("stop", "dur");
        assertThat(settings.primaryCancelKeyword()).isEqualTo("stop");
        assertThat(settings.isCancel("DUR")).isTrue();
        assertThat(settings.isCancel("cancel")).isFalse();
    }

    @Test
    void reloadSwapsTheParsedContent() throws IOException {
        write("default-mode = anvil\n");
        InputSettings settings = new InputSettings(dir.resolve("text-input.conf"), EngineLog.of(new SilentLogger()));
        assertThat(settings.modeFor("x")).isEqualTo(InputMode.ANVIL);

        write("default-mode = chat\n");
        settings.reload();
        assertThat(settings.modeFor("x")).isEqualTo(InputMode.CHAT);
    }

    @Test
    void malformedReloadKeepsThePreviousInputPolicy() throws IOException {
        write("default-mode = chat\ncancel-keywords = [\"stop\"]\n");
        InputSettings settings = new InputSettings(dir.resolve("text-input.conf"), EngineLog.of(new SilentLogger()));

        write("cancel-keywords = [\"broken\"\n");

        assertThatThrownBy(settings::reload).isInstanceOf(IllegalStateException.class);
        assertThat(settings.modeFor("x")).isEqualTo(InputMode.CHAT);
        assertThat(settings.cancelKeywords()).containsExactly("stop");
    }

    private void write(String content) throws IOException {
        Files.writeString(dir.resolve("text-input.conf"), content);
    }

    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
