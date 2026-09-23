package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every field on every item of a shipped window is one the menu engine reads.
 *
 * <p>Since uxmLib 0.141.0 the loader names an item field nobody reads. uxmCrates shipped one: its editor home wrote
 * {@code placeholders { tile-colour = "2" }} on the keys tile, a block the engine reads only at the root of a window,
 * so the tile was drawn in the window's own colour and nothing said so. This loads every window this plugin ships
 * and holds each to saying nothing of the kind.
 */
final class EveryWindowFieldIsReadTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    @DisplayName("no item of a shipped window carries a field the engine never reads")
    void everyItemFieldIsRead() throws IOException {
        List<String> unread = new ArrayList<>();
        Handler listening = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getMessage() != null && record.getMessage().contains("has no field")) {
                    unread.add(record.getMessage());
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        // The handler hangs on the loader's own logger, turned all the way up for the scan: a logger that sits above
        // WARNING in a test JVM would hand the handler nothing and let a typo through unseen.
        Logger loader = Logger.getLogger(MenuSpecLoader.class.getName());
        Level before = loader.getLevel();
        loader.setLevel(Level.ALL);
        loader.addHandler(listening);
        int loaded = 0;
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (Path file : files.filter(EveryWindowFieldIsReadTest::isAWindow).toList()) {
                try {
                    new MenuSpecLoader().parse(Files.readString(file, StandardCharsets.UTF_8));
                    loaded++;
                } catch (RuntimeException notAMenuSpec) {
                    // An editor layout or a list file shares the folder and has a loader of its own.
                }
            }
        } finally {
            loader.removeHandler(listening);
            loader.setLevel(before);
        }
        assertThat(loaded)
                .describedAs("a scan that loaded no window proves nothing")
                .isPositive();
        assertThat(unread).isEmpty();
    }

    private static boolean isAWindow(Path path) {
        String written = path.toString().replace('\\', '/');
        return written.endsWith(".conf") && (written.contains("/menus/") || written.contains("/gui/"));
    }
}
