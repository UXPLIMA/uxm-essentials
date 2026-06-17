package com.uxplima.uxmessentials.migration.convert.decentholograms;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.decentholograms.map.HologramMapper;
import com.uxplima.uxmessentials.migration.convert.decentholograms.parse.DhHologramParser;
import org.jspecify.annotations.NullMarked;

/**
 * The DecentHolograms source's lazy {@link ImportPlan}. It streams every {@code <name>.yml} under the
 * plugin's {@code holograms/} directory, parsing and mapping each on demand as the importer's bounded
 * executor drains it (docs/12-migration §7). A malformed or world-less file is dropped from the stream
 * (counted as skipped downstream), so one bad hologram file never aborts the run (§4).
 */
@NullMarked
final class DecentHologramsPlan implements ImportPlan {

    private final Path hologramsDirectory;
    private final DhHologramParser parser;
    private final HologramMapper mapper;

    DecentHologramsPlan(Path hologramsDirectory, HologramMapper mapper) {
        this.hologramsDirectory = Objects.requireNonNull(hologramsDirectory, "hologramsDirectory");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.parser = new DhHologramParser();
    }

    @Override
    public Stream<ImportRecord> records() {
        return ymlFiles(hologramsDirectory).flatMap(this::toRecord);
    }

    private Stream<ImportRecord> toRecord(Path file) {
        try {
            var parsed = parser.parse(file);
            if (parsed == null) {
                return Stream.empty();
            }
            return mapper.map(parsed).<ImportRecord>map(ImportRecord.HologramRecord::new).stream();
        } catch (IOException | RuntimeException badFile) {
            // One unreadable hologram file is skipped, never fatal (docs/12-migration §4).
            return Stream.empty();
        }
    }

    private static Stream<Path> ymlFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Stream.empty();
        }
        // Collect the listing eagerly inside try-with-resources so the directory handle is released at once;
        // the expensive per-file parse still happens lazily as the stream is drained (docs/12-migration §7).
        try (Stream<Path> listing = Files.list(dir)) {
            return listing.filter(p -> p.getFileName().toString().endsWith(".yml")).toList().stream();
        } catch (IOException unreadableDir) {
            throw new UncheckedIOException("failed to list " + dir, unreadableDir);
        }
    }

    @Override
    public void close() {
        // Each file is opened and closed by the parser per record; nothing is held open across the stream.
    }
}
