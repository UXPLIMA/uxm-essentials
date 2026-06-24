package com.uxplima.uxmessentials.custommenus.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * Reads the operator's {@code menus/*.conf} files into the running menu engine. Each top-level {@code .conf}
 * becomes a spec registered under its file name (sans extension): the file is parsed by the Phase-1
 * {@link MenuSpecLoader} and its refs validated against the registered {@link MenuBindings} before it reaches
 * {@link Menus}. A file that fails to parse, or that names an action/condition/placeholder no binding provides,
 * is skipped with a logged warning rather than aborting the whole load — one operator typo never hides every
 * other menu. An absent {@code menus/} directory is normal on a fresh install and yields an empty result.
 */
public final class CustomMenuLoader {

    private final MenuSpecLoader specLoader;
    private final MenuBindings bindings;
    private final Menus menus;
    private final Logger log;

    public CustomMenuLoader(MenuSpecLoader specLoader, MenuBindings bindings, Menus menus, Logger log) {
        this.specLoader = Objects.requireNonNull(specLoader, "specLoader");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The outcome of one load pass: how many specs registered, and the ids skipped over a parse or ref error. The
     * convenience constructor keeps the older {@code (count, skipped)} shape for callers and tests that only need the
     * counts; {@link #loadedNames()} carries the registered ids so {@code /menu list} can name what is open.
     */
    public record LoadResult(int loaded, List<String> loadedNames, List<String> skipped) {

        public LoadResult {
            loadedNames = List.copyOf(Objects.requireNonNull(loadedNames, "loadedNames"));
            skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
        }

        /** Build a result from the registered ids and the skipped ids, deriving the loaded count from the former. */
        public LoadResult(List<String> loadedNames, List<String> skipped) {
            this(loadedNames.size(), loadedNames, skipped);
        }
    }

    /**
     * Parse, validate and register every {@code *.conf} directly under {@code menusDir}. A missing directory is
     * treated as "no operator menus yet" and returns an empty result; a present directory is walked at its top
     * level, each file loaded independently so a single bad spec cannot fail the rest.
     */
    public LoadResult loadFrom(Path menusDir) {
        Objects.requireNonNull(menusDir, "menusDir");
        if (!Files.isDirectory(menusDir)) {
            return new LoadResult(List.of(), List.of());
        }
        List<String> loaded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        try (Stream<Path> entries = Files.list(menusDir)) {
            for (Path file : confFiles(entries)) {
                loadOne(file, loaded, skipped);
            }
        } catch (java.io.IOException failure) {
            log.warn("could not list menu directory {} : {}", menusDir, String.valueOf(failure.getMessage()));
        }
        log.info("loaded {} custom menus, skipped {}", loaded.size(), skipped.size());
        return new LoadResult(loaded, skipped);
    }

    /** The {@code .conf} files directly under the menus directory, in a stable order. */
    private static List<Path> confFiles(Stream<Path> entries) {
        return entries.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".conf"))
                .sorted()
                .toList();
    }

    /** Load one file; records its id in {@code loaded} when it registered, or in {@code skipped} on a parse/ref error. */
    private void loadOne(Path file, List<String> loaded, List<String> skipped) {
        String id = stripConf(file.getFileName().toString());
        MenuSpec spec;
        try {
            spec = specLoader.load(file);
        } catch (MenuSpecException invalid) {
            log.warn("skipped menu {} : {}", id, String.valueOf(invalid.getMessage()));
            skipped.add(id);
            return;
        }
        List<String> missing = bindings.validate(List.of(spec));
        if (!missing.isEmpty()) {
            log.warn("skipped menu {} : references unknown ids {}", id, missing);
            skipped.add(id);
            return;
        }
        menus.registerSpec(id, spec);
        loaded.add(id);
    }

    private static String stripConf(String fileName) {
        return fileName.substring(0, fileName.length() - ".conf".length());
    }
}
