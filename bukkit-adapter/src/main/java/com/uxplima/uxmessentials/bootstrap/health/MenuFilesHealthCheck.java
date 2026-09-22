package com.uxplima.uxmessentials.bootstrap.health;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * The windows line of {@code /uxmess doctor}: how many of the operator's own menu files the engine accepted.
 *
 * <p>A window is a file an operator edits, and a file that did not parse is a menu that does not open with no
 * line anywhere saying why, except one in a console log from startup that nobody keeps. So the check reads two
 * numbers rather than one: what is in {@code menus/}, and what the loader registered. An empty folder and a
 * folder of broken files are the same number on their own, and only the second is a fault.
 *
 * <p>Nothing here re-reads or re-parses anything. The count of loaded names comes from the live supplier the
 * custom-menus wiring publishes, so a {@code /menu reload} changes this line with no help from it.
 */
@NullMarked
public final class MenuFilesHealthCheck implements HealthCheck {

    /** The two files that live in {@code menus/} and are not menus. */
    private static final Set<String> NOT_A_MENU = Set.of("openers.conf", "placeholders.conf");

    private final Path menusDir;
    private final Supplier<List<String>> loaded;

    public MenuFilesHealthCheck(Path menusDir, Supplier<List<String>> loaded) {
        this.menusDir = Objects.requireNonNull(menusDir, "menusDir");
        this.loaded = Objects.requireNonNull(loaded, "loaded");
    }

    @Override
    public String name() {
        return "windows";
    }

    @Override
    public HealthResult check() {
        int onDisk = filesOnDisk();
        int read = loaded.get().size();
        if (onDisk == 0) {
            return HealthResult.ok("no operator window: menus/ holds none");
        }
        if (read == 0) {
            return HealthResult.fail(onDisk + " files in menus/ and none was read: every one was refused, and"
                    + " the reason for each is in the console from startup");
        }
        if (read < onDisk) {
            return HealthResult.warn(read + " of " + onDisk + " read: the rest were refused, and the reason"
                    + " for each is in the console from startup");
        }
        return HealthResult.ok(read + " read");
    }

    /**
     * The menu files an operator has written. A folder that is not there is not an error: the loader makes it
     * on first boot and an operator who deletes it has simply written no window.
     */
    private int filesOnDisk() {
        if (!Files.isDirectory(menusDir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(menusDir)) {
            return (int) files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".conf"))
                    .filter(name -> !NOT_A_MENU.contains(name))
                    .count();
        } catch (IOException unreadable) {
            return 0;
        }
    }
}
