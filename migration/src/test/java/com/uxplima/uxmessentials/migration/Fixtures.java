package com.uxplima.uxmessentials.migration;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/** The fixture files under {@code src/test/resources} and the server they are imported into. */
public final class Fixtures {

    /** The one world the test server has; every other world name a fixture uses is unknown to it. */
    public static final WorldRef WORLD = new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000a001"), "world");

    /** Resolves {@code world} and nothing else, as a server that never loaded the source's other worlds. */
    public static final WorldNameResolver ONLY_WORLD =
            name -> WORLD.name().equals(name) ? Optional.of(WORLD) : Optional.empty();

    private Fixtures() {}

    /** The fixture file or directory at {@code resource}, such as {@code /essentialsx}. */
    public static Path path(String resource) {
        URL url = Objects.requireNonNull(Fixtures.class.getResource(resource), resource);
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException badUrl) {
            throw new IllegalStateException(badUrl);
        }
    }

    /** Every record a live import of {@code source} would hand to the writer, drained the way the importer drains. */
    public static List<ImportRecord> records(Convert source, Path sourcePath) {
        try (ImportPlan plan = source.plan(ImportOptions.live(sourcePath));
                Stream<ImportRecord> records = plan.records()) {
            return records.toList();
        }
    }

    /** The records of one kind, in stream order. */
    public static <T extends ImportRecord> List<T> only(List<ImportRecord> records, Class<T> kind) {
        return records.stream().filter(kind::isInstance).map(kind::cast).toList();
    }
}
