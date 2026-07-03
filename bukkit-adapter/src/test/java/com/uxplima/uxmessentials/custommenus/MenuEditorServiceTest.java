package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService.EditOutcome;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plain-JUnit coverage of {@link MenuEditorService}, the file-level CRUD over {@code menus/*.conf} the editor drives.
 * The registered specs and the on-disk files are stood up in a temp directory; the service creates, duplicates,
 * renames, and deletes through the shared {@link MenuSpecPersistence}, and its typed outcomes gate the editor's
 * feedback. Name validation (safe filename, reserved names, collisions) is exercised so the editor never writes a
 * menu the loader would choke on.
 */
class MenuEditorServiceTest {

    private final MenuSpecLoader loader = new MenuSpecLoader();
    private final Map<String, MenuSpec> registered = new ConcurrentHashMap<>();

    @TempDir
    Path menusDir;

    private MenuEditorService service;

    @BeforeEach
    void setUp() throws IOException {
        MenuBindings bindings = new MenuBindings();
        bindings.action("close", ctx -> {});
        MenuSpecPersistence persistence = new MenuSpecPersistence(new MenuSpecWriter(), bindings, NOOP);
        MenuSpec shop = loader.parse(
                "title = \"<gold>Shop\"\nrows = 1\nitems { buy { slot = 0, material = EMERALD, click { left = [\"close\"] } } }");
        registered.put("shop", shop);
        Files.writeString(menusDir.resolve("shop.conf"), new MenuSpecWriter().write(shop));
        service = new MenuEditorService(
                menusDir,
                persistence,
                name -> Optional.ofNullable(registered.get(name)),
                name -> Optional.empty(),
                this::reloadOne,
                registered::remove,
                NOOP);
    }

    /** A test double for the wiring's single-file reload: re-parse the written file into the registered map. */
    private CustomMenuLoader.SingleLoad reloadOne(String name) {
        Path file = menusDir.resolve(name + ".conf");
        if (!Files.isRegularFile(file)) {
            return new CustomMenuLoader.SingleLoad(name, false, 0, 0);
        }
        registered.put(name, loader.parse(readString(file)));
        return new CustomMenuLoader.SingleLoad(name, true, 1, 0);
    }

    @Test
    void createBlankWritesALoadableFileAndRegistersIt() {
        EditOutcome outcome = service.createBlank("welcome");

        assertThat(outcome).isEqualTo(EditOutcome.CREATED);
        assertThat(Files.exists(menusDir.resolve("welcome.conf"))).isTrue();
        assertThat(registered).containsKey("welcome");
        // The blank file re-loads through the real loader without error.
        assertThat(loader.parse(readString(menusDir.resolve("welcome.conf")))).isNotNull();
    }

    @Test
    void createBlankRejectsAnUnsafeFileName() {
        assertThat(service.createBlank("bad name")).isEqualTo(EditOutcome.NAME_INVALID);
        assertThat(service.createBlank("../escape")).isEqualTo(EditOutcome.NAME_INVALID);
        assertThat(service.createBlank("")).isEqualTo(EditOutcome.NAME_INVALID);
    }

    @Test
    void createBlankRejectsAReservedName() {
        assertThat(service.createBlank("openers")).isEqualTo(EditOutcome.NAME_RESERVED);
        assertThat(service.createBlank("patterns")).isEqualTo(EditOutcome.NAME_RESERVED);
        assertThat(service.createBlank("placeholders")).isEqualTo(EditOutcome.NAME_RESERVED);
        assertThat(service.createBlank("example")).isEqualTo(EditOutcome.NAME_RESERVED);
    }

    @Test
    void createBlankRejectsANameAlreadyInUse() {
        assertThat(service.createBlank("shop")).isEqualTo(EditOutcome.NAME_TAKEN);
        assertThat(Files.exists(menusDir.resolve("shop.conf"))).isTrue();
    }

    @Test
    void duplicateCopiesTheSourceSpecUnderTheNewName() {
        EditOutcome outcome = service.duplicate("shop", "shop2");

        assertThat(outcome).isEqualTo(EditOutcome.DUPLICATED);
        assertThat(Files.exists(menusDir.resolve("shop2.conf"))).isTrue();
        // The copy re-loads to the same model the source registered.
        assertThat(registered.get("shop2")).isEqualTo(registered.get("shop"));
    }

    @Test
    void duplicateFromAnUnknownMenuReportsSourceMissing() {
        assertThat(service.duplicate("ghost", "copy")).isEqualTo(EditOutcome.SOURCE_MISSING);
        assertThat(Files.exists(menusDir.resolve("copy.conf"))).isFalse();
    }

    @Test
    void renameMovesTheFileAndUnregistersTheOldName() {
        EditOutcome outcome = service.rename("shop", "market");

        assertThat(outcome).isEqualTo(EditOutcome.RENAMED);
        assertThat(Files.exists(menusDir.resolve("market.conf"))).isTrue();
        assertThat(Files.exists(menusDir.resolve("shop.conf"))).isFalse();
        assertThat(registered).containsKey("market").doesNotContainKey("shop");
    }

    @Test
    void renameOntoAnExistingNameIsRejected() {
        service.createBlank("taken");

        assertThat(service.rename("shop", "taken")).isEqualTo(EditOutcome.NAME_TAKEN);
        // The source is untouched by a rejected rename.
        assertThat(Files.exists(menusDir.resolve("shop.conf"))).isTrue();
        assertThat(registered).containsKey("shop");
    }

    @Test
    void deleteRemovesTheFileAndUnregistersTheSpec() {
        EditOutcome outcome = service.delete("shop");

        assertThat(outcome).isEqualTo(EditOutcome.DELETED);
        assertThat(Files.exists(menusDir.resolve("shop.conf"))).isFalse();
        assertThat(registered).doesNotContainKey("shop");
    }

    @Test
    void deleteOfAnUnknownMenuReportsSourceMissing() {
        assertThat(service.delete("ghost")).isEqualTo(EditOutcome.SOURCE_MISSING);
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + file, failure);
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };
}
