package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guards the three generic GUI shells: {@code EntityListView}, {@code EntityEditorView} and
 * {@code SettingsPanelView} draw every module's management screens, so a material named inside one of them is a
 * material no operator can change. Their presentation must come from the caller's layout (a
 * {@code modules/<m>/gui/<name>.conf} file, or the code default that file falls back to), never from a literal in
 * the shell.
 *
 * <p>The second check pins the two side controls that used to carry their slot and material at the call site: the
 * regions flag editor's roster button and the announcement list's settings button now read {@code action-slot} and
 * {@code action-icon} from their layout files, so a shipped conf that dropped those keys would silently hide the
 * button.
 */
class GuiShellsAreLayoutDrivenDriftTest {

    private static final String[] SHELLS = {"EntityListView", "EntityEditorView", "SettingsPanelView"};

    @Test
    void theGenericShellsNameNoMaterial() {
        for (String shell : SHELLS) {
            Path source = Path.of(
                    "bukkit-adapter/src/main/java/com/uxplima/uxmessentials/shared/adapter/inbound/gui",
                    shell + ".java");
            assertThat(read(source))
                    .as(shell + " reads its materials from the layout, never from a literal")
                    .doesNotContain("Material.")
                    .doesNotContain("import org.bukkit.Material;");
        }
    }

    @Test
    void everyListWithASideControlDeclaresItsSlotAndIcon() {
        for (String[] layout :
                new String[][] {{"regions", "region-flags"}, {"communication", "announcement-editor-list"}}) {
            Path file = repoRoot()
                    .resolve("bukkit-adapter/src/main/resources/modules")
                    .resolve(layout[0])
                    .resolve("gui")
                    .resolve(layout[1] + ".conf");
            assertThat(read(file))
                    .as(layout[0] + "/" + layout[1] + " declares its action button")
                    .contains("action-slot =")
                    .contains("action-icon =");
        }
    }

    private static String read(Path relativeOrAbsolute) {
        Path file = relativeOrAbsolute.isAbsolute()
                ? relativeOrAbsolute
                : repoRoot().resolve(relativeOrAbsolute);
        assertThat(file).as(file.toString()).exists();
        try {
            return Files.readString(file);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("could not read " + file, failure);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
