package com.uxplima.uxmessentials.migration.convert.multiverse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.map.ImportedWorld;
import com.uxplima.uxmessentials.migration.convert.multiverse.map.MultiverseWorldMapper;
import com.uxplima.uxmessentials.migration.convert.multiverse.parse.MultiverseWorld;
import com.uxplima.uxmessentials.migration.convert.multiverse.parse.MultiverseWorldParser;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The Multiverse source's lazy {@link ImportPlan}. The whole registry lives in one {@code worlds.yml}, so the file is
 * parsed once into its entries and each is mapped on demand as the importer's bounded executor drains the stream
 * (docs/12-migration §7). An unreadable file yields no records rather than aborting the run, and a single broken
 * entry is skipped rather than failing the batch (§4).
 *
 * <p>An entry our world registry cannot name is dropped from the stream and said so in the log, so an operator can
 * see exactly which worlds did not come across instead of discovering the gap in {@code /world list}.
 */
@NullMarked
final class MultiversePlan implements ImportPlan {

    private final Path worldsFile;
    private final MultiverseWorldParser parser;
    private final MultiverseWorldMapper mapper;
    private final Logger log;

    MultiversePlan(Path worldsFile, MultiverseWorldMapper mapper, Logger log) {
        this.worldsFile = Objects.requireNonNull(worldsFile, "worldsFile");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.log = Objects.requireNonNull(log, "log");
        this.parser = new MultiverseWorldParser();
    }

    @Override
    public Stream<ImportRecord> records() {
        return parsed().stream().flatMap(this::toRecord);
    }

    private List<MultiverseWorld> parsed() {
        try {
            return parser.parse(worldsFile);
        } catch (IOException | RuntimeException badFile) {
            // An unreadable worlds.yml is skipped, never fatal (docs/12-migration §4).
            log.warn("Multiverse worlds.yml could not be read, importing nothing: {}", String.valueOf(worldsFile));
            return List.of();
        }
    }

    private Stream<ImportRecord> toRecord(MultiverseWorld world) {
        try {
            Optional<ImportedWorld> mapped = mapper.map(world);
            if (mapped.isEmpty()) {
                log.warn("skipping the Multiverse world {}: our world registry cannot hold that name", world.name());
                return Stream.empty();
            }
            return Stream.of(new ImportRecord.WorldRecord(mapped.get()));
        } catch (RuntimeException badWorld) {
            log.warn(
                    "skipping a malformed Multiverse world {}: {}",
                    world.name(),
                    String.valueOf(badWorld.getMessage()));
            return Stream.empty();
        }
    }

    @Override
    public void close() {
        // The file is read once up front; nothing is held open across the stream.
    }
}
