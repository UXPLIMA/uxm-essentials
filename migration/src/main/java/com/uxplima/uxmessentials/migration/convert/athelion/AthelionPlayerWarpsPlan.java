package com.uxplima.uxmessentials.migration.convert.athelion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.athelion.map.AthelionWarpMapper;
import com.uxplima.uxmessentials.migration.convert.athelion.parse.AthelionWarp;
import com.uxplima.uxmessentials.migration.convert.athelion.parse.AthelionWarpParser;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The Athelion source's lazy {@link ImportPlan}. Athelion keeps every warp in one {@code data.yml}, so the file is parsed
 * once into its entries and each is mapped on demand as the importer's bounded executor drains the stream
 * (docs/12-migration §7). A warp whose world the live server does not know is dropped from the stream and counted as
 * skipped downstream (§4); an unreadable {@code data.yml} yields no records rather than aborting the run, and a single
 * malformed entry is skipped rather than failing the batch.
 */
@NullMarked
final class AthelionPlayerWarpsPlan implements ImportPlan {

    private final Path dataFile;
    private final AthelionWarpParser parser;
    private final AthelionWarpMapper mapper;
    private final Logger log;

    AthelionPlayerWarpsPlan(Path dataFile, AthelionWarpMapper mapper, Logger log) {
        this.dataFile = Objects.requireNonNull(dataFile, "dataFile");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.log = Objects.requireNonNull(log, "log");
        this.parser = new AthelionWarpParser();
    }

    @Override
    public Stream<ImportRecord> records() {
        return parsed().stream().flatMap(this::toRecord);
    }

    private List<AthelionWarp> parsed() {
        try {
            return parser.parse(dataFile);
        } catch (IOException | RuntimeException badFile) {
            // An unreadable data.yml is skipped, never fatal (docs/12-migration §4).
            log.warn("Athelion data.yml could not be read, importing nothing: {}", String.valueOf(dataFile));
            return List.of();
        }
    }

    private Stream<ImportRecord> toRecord(AthelionWarp warp) {
        try {
            return mapper.map(warp).<ImportRecord>map(ImportRecord.PlayerWarpRecord::new).stream();
        } catch (RuntimeException badWarp) {
            log.warn("skipping a malformed Athelion warp {}: {}", warp.name(), String.valueOf(badWarp.getMessage()));
            return Stream.empty();
        }
    }

    @Override
    public void close() {
        // The file is read once up front; nothing is held open across the stream.
    }
}
