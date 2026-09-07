package com.uxplima.uxmessentials.custommenus.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;

/**
 * The header a menu file teaches itself with, and what a save keeps of it.
 *
 * <p>{@link MenuSpecWriter} rebuilds a file out of the model, so everything the model does not hold is gone the first
 * time an operator presses save in the editor. That took the header with it: the block of comments that opens the
 * shipped {@code menus/example.conf} is the only place an operator is told the grammar of the file they are editing,
 * and one save left them with a menu that works and a file that teaches nothing.
 *
 * <p>The header is a bundled resource now and the writer re-emits it. These tests hold three things: that it is
 * emitted, that the shipped example carries the same text so the two cannot drift apart, and that the file still
 * loads afterwards. The fourth records what is deliberately still lost.
 */
class MenuSpecHeaderTest {

    private final MenuSpecLoader loader = new MenuSpecLoader();
    private final MenuSpecWriter writer = new MenuSpecWriter();

    private static final String MENU = """
            title = "<gold>Shop"
            rows = 3
            items { a { slot = 0, material = STONE, click { left = ["close"] } } }
            """;

    @Test
    void everySavedMenuOpensWithTheShippedHeader() {
        String written = writer.write(loader.parse(MENU));

        assertThat(written).startsWith(header().stripTrailing());
    }

    /**
     * The header exists in two places, and they have to say the same thing: the resource the writer emits, and the
     * example we ship. Without this, the example could teach one grammar and every saved menu another, and nobody
     * would notice until an operator compared two files.
     */
    @Test
    void theShippedExampleOpensWithTheSameHeader() {
        assertThat(readResource("/menus/example.conf")).startsWith(header());
    }

    @Test
    void aFileWithTheHeaderOnItStillLoadsAndStillRoundTrips() {
        MenuSpec original = loader.parse(MENU);

        MenuSpec reloaded = loader.parse(writer.write(original));

        assertThat(reloaded).isEqualTo(original);
    }

    /**
     * The accepted loss, written as a test so that nobody reads it as a defect later. A note an operator wrote beside
     * their own line does not survive a save, and neither does the order they wrote their keys in. Keeping them would
     * mean carrying every comment, every blank line and every key position through an editor that adds, removes and
     * reorders items, which is a second parser and a second model. The header itself tells the operator this.
     */
    @Test
    void anOperatorsOwnCommentIsNotKept() {
        String withNote = """
                # An operator wrote this line and it will not survive a save.
                title = "<gold>Shop"
                rows = 3
                items { a { slot = 0, material = STONE, click { left = ["close"] } } }
                """;

        String written = writer.write(loader.parse(withNote));

        assertThat(written).doesNotContain("An operator wrote this line");
        assertThat(written)
                .as("but the header that says so is there")
                .contains("it does not keep a comment you wrote yourself");
    }

    private static String header() {
        return readResource("/menus/header.txt");
    }

    private static String readResource(String path) {
        try (InputStream bundled = MenuSpecHeaderTest.class.getResourceAsStream(path)) {
            if (bundled == null) {
                throw new IllegalStateException(path + " is not on the test classpath");
            }
            return new String(bundled.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
