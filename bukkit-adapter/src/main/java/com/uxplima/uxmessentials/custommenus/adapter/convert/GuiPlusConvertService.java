package com.uxplima.uxmessentials.custommenus.adapter.convert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The one-shot admin service behind {@code /menu convert guiplus <path>}: it reads a GUIPlus GUI YAML (or every
 * {@code .yml} in a directory), runs each through {@link GuiPlusConverter}, and writes the result as a
 * {@code menus/<name>.conf} the engine loads on the next {@code /menu reload}. It mirrors
 * {@link DeluxeMenusConvertService}, {@link ZMenuConvertService} and {@link OguiConvertService} exactly — same
 * file-walk through {@link MenuConvertFiles}, same write-and-tally, same never-reload / never-crash contract —
 * differing only in the converter it drives. A file that fails to read or parse is counted as skipped and logged, so
 * one malformed export never aborts a whole directory convert.
 *
 * <p>The path argument is absolute or relative to the menus directory, so an operator can point at a copied
 * {@code GUIPlus/guis} folder anywhere on disk. Runs synchronously on the caller's thread: it is an operator one-shot
 * over a handful of small files, not a hot path.
 */
public final class GuiPlusConvertService {

    /** The output extension every converted menu is written under — the extension the engine's loader reads. */
    private static final String OUTPUT_EXTENSION = ".conf";

    private final Path menusDir;
    private final GuiPlusConverter converter;
    private final Logger log;

    public GuiPlusConvertService(Path menusDir, GuiPlusConverter converter, Logger log) {
        this.menusDir = Objects.requireNonNull(menusDir, "menusDir");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Convert the GUIPlus YAML at {@code rawPath} — a single file or a directory of them — into our menus directory. A
     * path that resolves to nothing (a missing file, a directory holding no {@code .yml}) yields a not-found report;
     * otherwise every discovered file is converted independently and its outcome tallied.
     */
    public ConvertReport convert(String rawPath) {
        Objects.requireNonNull(rawPath, "rawPath");
        List<Path> inputs = MenuConvertFiles.resolveInputs(menusDir, rawPath, log);
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
            GuiPlusConverter.ConversionResult result = converter.convert(Files.readString(input));
            Files.createDirectories(menusDir);
            String name = MenuConvertFiles.baseName(input);
            Files.writeString(menusDir.resolve(name + OUTPUT_EXTENSION), result.hocon());
            result.warnings().forEach(warning -> log.warn("menu-convert {} : {}", name, warning));
            return result.warnings().size();
        } catch (IOException | RuntimeException failure) {
            log.warn("menu-convert skipped {} : {}", input, String.valueOf(failure.getMessage()));
            return -1;
        }
    }

    /**
     * The tally of one convert run: whether any input was found at all, and how many files converted, how many were
     * skipped over a read/parse error, and how many best-effort warnings the survivors accrued.
     */
    public record ConvertReport(boolean found, int converted, int skipped, int warnings) {

        /** The report for a path that matched no GUIPlus YAML — the command turns this into a not-found reply. */
        public static ConvertReport notFound() {
            return new ConvertReport(false, 0, 0, 0);
        }
    }
}
