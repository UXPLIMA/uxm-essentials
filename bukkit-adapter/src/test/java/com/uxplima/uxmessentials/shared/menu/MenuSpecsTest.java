package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shared loader that replaced the per-menu copy of the disk-first, bundled-fallback resolution. This pins the
 * two ends of that policy: an operator's on-disk edit is preferred when present and valid, and a resource absent from
 * both disk and the jar degrades to an empty spec of the caller's requested row count rather than aborting wiring.
 */
class MenuSpecsTest {

    @TempDir
    Path dataFolder;

    @Test
    void prefersTheOperatorFileOnDisk() throws IOException {
        String resource = "modules/example/gui/example-menu.conf";
        Path onDisk = dataFolder.resolve(resource);
        Files.createDirectories(onDisk.getParent());
        Files.writeString(onDisk, "title = \"Operator Menu\"\nrows = 5\n", StandardCharsets.UTF_8);

        MenuSpec spec = MenuSpecs.loadOrBundled(resource, dataFolder, 6, new NoopLogger());

        assertThat(spec.title()).isEqualTo("Operator Menu");
        assertThat(spec.rows()).isEqualTo(5);
    }

    @Test
    void fallsBackToEmptySpecOfRequestedRowsWhenResourceIsMissing() {
        String resource = "modules/example/gui/does-not-exist.conf";

        MenuSpec spec = MenuSpecs.loadOrBundled(resource, dataFolder, 4, new NoopLogger());

        assertThat(spec.title()).isEmpty();
        assertThat(spec.rows()).isEqualTo(4);
        assertThat(spec.items()).isEmpty();
    }

    private static final class NoopLogger implements Logger {
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
