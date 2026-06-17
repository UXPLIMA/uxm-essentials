package com.uxplima.uxmessentials.migration.convert.fancyholograms;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.decentholograms.map.HologramMapper;
import com.uxplima.uxmessentials.migration.convert.decentholograms.parse.DhHologram;
import com.uxplima.uxmessentials.migration.convert.fancyholograms.parse.FhHologramParser;
import org.jspecify.annotations.NullMarked;

/**
 * The FancyHolograms source's lazy {@link ImportPlan}. FancyHolograms keeps every hologram in one
 * {@code holograms.yml}, so the file is parsed once into its entries and each is mapped on demand as the
 * importer's bounded executor drains the stream (docs/12-migration §7). A hologram whose world the server
 * does not know, or that carries no text line (item and block holograms), is dropped from the stream and
 * counted as skipped downstream (§4); an unreadable file yields no records rather than aborting the run.
 */
@NullMarked
final class FancyHologramsPlan implements ImportPlan {

    private final Path hologramsFile;
    private final FhHologramParser parser;
    private final HologramMapper mapper;

    FancyHologramsPlan(Path hologramsFile, HologramMapper mapper) {
        this.hologramsFile = Objects.requireNonNull(hologramsFile, "hologramsFile");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.parser = new FhHologramParser();
    }

    @Override
    public Stream<ImportRecord> records() {
        return parsed().stream().flatMap(this::toRecord);
    }

    private List<DhHologram> parsed() {
        try {
            return parser.parse(hologramsFile);
        } catch (IOException | RuntimeException badFile) {
            // An unreadable holograms.yml is skipped, never fatal (docs/12-migration §4).
            return List.of();
        }
    }

    private Stream<ImportRecord> toRecord(DhHologram hologram) {
        return mapper.map(hologram).<ImportRecord>map(ImportRecord.HologramRecord::new).stream();
    }

    @Override
    public void close() {
        // The file is read once up front; nothing is held open across the stream.
    }
}
