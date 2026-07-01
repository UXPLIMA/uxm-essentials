package com.uxplima.uxmessentials.custommenus.adapter.convert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The one-shot admin service behind {@code /menu convert deluxemenus <path>}: it reads a DeluxeMenus menu YAML (or
 * every {@code .yml} in a directory), runs each through {@link DeluxeMenusConverter}, and writes the result as a
 * {@code menus/<name>.conf} the engine loads on the next {@code /menu reload}. It never reloads itself — an operator
 * reviews the emitted files first — and never crashes on a bad input: a file that fails to read or parse is counted
 * as skipped and logged, so one malformed export never aborts a whole directory convert.
 *
 * <p>The path argument is resolved against the {@code menus/} directory when relative, or taken as-is when absolute,
 * so an operator can point at a copied {@code DeluxeMenus/menus} folder anywhere on disk. This reads and writes files
 * synchronously on the caller's thread: it is an operator one-shot over a handful of small files, not a hot path, so
 * the simplicity is worth more than pushing the I/O onto the scheduler.
 */
public final class DeluxeMenusConvertService {

    /** The output extension every converted menu is written under — the extension the engine's loader reads. */
    private static final String OUTPUT_EXTENSION = ".conf";

    private final Path menusDir;
    private final DeluxeMenusConverter converter;
    private final Logger log;

    public DeluxeMenusConvertService(Path menusDir, DeluxeMenusConverter converter, Logger log) {
        this.menusDir = Objects.requireNonNull(menusDir, "menusDir");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Convert the DeluxeMenus YAML at {@code rawPath} — a single file or a directory of them — into our menus
     * directory. A path that resolves to nothing (a missing file, a directory holding no {@code .yml}) yields a
     * not-found report; otherwise every discovered file is converted independently and its outcome tallied.
     */
    public ConvertReport convert(String rawPath) {
        Objects.requireNonNull(rawPath, "rawPath");
        List<Path> inputs = resolveInputs(rawPath);
        if (inputs.isEmpty()) {
            return ConvertReport.notFound();
        }
        int converted = 0;
        int skipped = 0;
        int warnings = 0;
        for (Path input : inputs) {
            int emitted = convertOne(input);
            if (emitted < 0) {
                skipped++;
            } else {
                converted++;
                warnings += emitted;
            }
        }
        return new ConvertReport(true, converted, skipped, warnings);
    }

    /** Convert one file, returning its warning count on success or {@code -1} when it could not be read/written. */
    private int convertOne(Path input) {
        try {
            DeluxeMenusConverter.ConversionResult result = converter.convert(Files.readString(input));
            Files.createDirectories(menusDir);
            Files.writeString(menusDir.resolve(baseName(input) + OUTPUT_EXTENSION), result.hocon());
            result.warnings().forEach(warning -> log.warn("menu-convert {} : {}", baseName(input), warning));
            return result.warnings().size();
        } catch (IOException | RuntimeException failure) {
            log.warn("menu-convert skipped {} : {}", input, String.valueOf(failure.getMessage()));
            return -1;
        }
    }

    /** Resolve the raw path to the concrete {@code .yml} inputs: a directory's contents, a single file, or none. */
    private List<Path> resolveInputs(String rawPath) {
        Path resolved = resolve(rawPath);
        if (resolved == null) {
            return List.of();
        }
        if (Files.isDirectory(resolved)) {
            return ymlFiles(resolved);
        }
        return Files.isRegularFile(resolved) && isYaml(resolved) ? List.of(resolved) : List.of();
    }

    /** Interpret {@code rawPath} as absolute or relative to the menus directory; a malformed path resolves to none. */
    private @Nullable Path resolve(String rawPath) {
        try {
            Path path = Path.of(rawPath);
            return path.isAbsolute() ? path : menusDir.resolve(path);
        } catch (InvalidPathException malformed) {
            log.warn("menu-convert could not read path {} : {}", rawPath, String.valueOf(malformed.getMessage()));
            return null;
        }
    }

    /** Every {@code .yml}/{@code .yaml} file directly under a directory, in a stable order. */
    private List<Path> ymlFiles(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile)
                    .filter(DeluxeMenusConvertService::isYaml)
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            log.warn("menu-convert could not list {} : {}", dir, String.valueOf(failure.getMessage()));
            return List.of();
        }
    }

    /** Whether a file name ends in a YAML extension DeluxeMenus writes. */
    private static boolean isYaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    /** The file name without its YAML extension — the id the converted {@code .conf} is written under. */
    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /**
     * The tally of one convert run: whether any input was found at all, and how many files converted, how many were
     * skipped over a read/parse error, and how many best-effort warnings the survivors accrued.
     */
    public record ConvertReport(boolean found, int converted, int skipped, int warnings) {

        /** The report for a path that matched no DeluxeMenus YAML — the command turns this into a not-found reply. */
        public static ConvertReport notFound() {
            return new ConvertReport(false, 0, 0, 0);
        }
    }
}
