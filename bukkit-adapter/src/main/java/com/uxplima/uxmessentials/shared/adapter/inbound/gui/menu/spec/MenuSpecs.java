package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * Shared loader for the HOCON menu specs every module ships. It prefers an operator's edit on disk, falls
 * back to the bundled classpath resource, and finally to a minimal empty spec, so a typo or a missing file
 * degrades to a closeable empty window rather than aborting a module's wiring. Keeping the disk-first,
 * classpath-second policy in one place is why the menu classes no longer each copy it.
 */
public final class MenuSpecs {

    private MenuSpecs() {}

    /**
     * Resolve a menu spec: the operator's copy under {@code dataFolder} when present and valid, otherwise the
     * bundled default, otherwise an empty {@code fallbackRows}-row spec.
     *
     * @param resource classpath and on-disk resource path of the spec
     * @param dataFolder plugin data folder searched first
     * @param fallbackRows row count for the empty last-resort spec
     * @param log where load failures are reported
     * @return a usable spec, never null
     */
    public static MenuSpec loadOrBundled(String resource, Path dataFolder, int fallbackRows, Logger log) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        MenuSpecLoader specLoader = new MenuSpecLoader();
        Path onDisk = dataFolder.resolve(resource);
        if (Files.isRegularFile(onDisk)) {
            try {
                return specLoader.load(onDisk);
            } catch (MenuSpecException malformed) {
                log.error("failed to load menu spec " + onDisk + ", using bundled default", malformed);
            }
        }
        return loadBundled(specLoader, resource, fallbackRows, log);
    }

    private static MenuSpec loadBundled(MenuSpecLoader specLoader, String resource, int fallbackRows, Logger log) {
        try (InputStream in = MenuSpecs.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("bundled menu spec {} is missing from the jar", resource);
                return emptySpec(fallbackRows);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return specLoader.parse(reader.lines().collect(Collectors.joining("\n")));
            }
        } catch (IOException | MenuSpecException failure) {
            log.error("could not read bundled menu spec " + resource, failure);
            return emptySpec(fallbackRows);
        }
    }

    private static MenuSpec emptySpec(int rows) {
        return new MenuSpec("", rows, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }
}
