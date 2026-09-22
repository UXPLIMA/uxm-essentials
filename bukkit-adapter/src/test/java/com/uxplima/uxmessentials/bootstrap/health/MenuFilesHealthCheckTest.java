package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The windows line of {@code /uxmess doctor}.
 *
 * <p>A window is a file an operator edits, so the two numbers that matter are how many files are there and how
 * many of them the engine accepted. One number cannot tell an empty folder from a folder of broken files, and
 * a folder of broken files is the case the operator needs to hear about: the menu does not open, and the only
 * other sign is a line in a console log from startup.
 */
class MenuFilesHealthCheckTest {

    @Test
    @DisplayName("an empty menus folder is not a fault")
    void noFilesIsFine(@TempDir Path folder) {
        MenuFilesHealthCheck check = new MenuFilesHealthCheck(folder.resolve("menus"), List::of);

        assertThat(check.check().status()).isEqualTo(HealthStatus.OK);
        assertThat(check.check().message()).contains("none");
    }

    @Test
    @DisplayName("every file read is the ordinary answer")
    void everyFileRead(@TempDir Path folder) throws IOException {
        Path menus = menusWith(folder, "shop.conf", "warps.conf");

        MenuFilesHealthCheck check = new MenuFilesHealthCheck(menus, () -> List.of("shop", "warps"));

        assertThat(check.check().status()).isEqualTo(HealthStatus.OK);
        assertThat(check.check().message()).contains("2");
    }

    @Test
    @DisplayName("files that are there and none read is a failure, because no menu opens")
    void nothingRead(@TempDir Path folder) throws IOException {
        Path menus = menusWith(folder, "shop.conf", "warps.conf");

        MenuFilesHealthCheck check = new MenuFilesHealthCheck(menus, List::of);

        assertThat(check.check().status()).isEqualTo(HealthStatus.FAIL);
        assertThat(check.check().message()).contains("2");
    }

    @Test
    @DisplayName("some read and some refused is a warning naming both numbers")
    void someRefused(@TempDir Path folder) throws IOException {
        Path menus = menusWith(folder, "shop.conf", "warps.conf", "kits.conf");

        MenuFilesHealthCheck check = new MenuFilesHealthCheck(menus, () -> List.of("shop"));

        assertThat(check.check().status()).isEqualTo(HealthStatus.WARN);
        assertThat(check.check().message()).contains("1").contains("3");
    }

    @Test
    @DisplayName("the two files that are not menus are not counted as menus")
    void thesidecarFilesAreNotMenus(@TempDir Path folder) throws IOException {
        Path menus = menusWith(folder, "openers.conf", "placeholders.conf");

        MenuFilesHealthCheck check = new MenuFilesHealthCheck(menus, List::of);

        assertThat(check.check().status())
                .describedAs("openers.conf and placeholders.conf sit in menus/ and are not windows, so a"
                        + " folder holding only those is a folder with no window in it")
                .isEqualTo(HealthStatus.OK);
    }

    private static Path menusWith(Path folder, String... names) throws IOException {
        Path menus = Files.createDirectories(folder.resolve("menus"));
        for (String name : names) {
            Files.writeString(menus.resolve(name), "title = \"x\"\n", StandardCharsets.UTF_8);
        }
        return menus;
    }
}
