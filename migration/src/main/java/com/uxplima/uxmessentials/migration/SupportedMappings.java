package com.uxplima.uxmessentials.migration;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.essentialsx.EssentialsXMappings;
import org.jspecify.annotations.NullMarked;

/**
 * Aggregates every <em>built</em> source's per-source mapping table (docs/12-migration §5). It is the
 * single greppable answer to "what does the importer claim to migrate?", scoped by source. Only built
 * sources contribute rows — a planned source has no code table, so it appears nowhere here until it is
 * built (planned ≠ stubbed, §1.2). The drift guard reads this aggregate to assert the three-way equality
 * code ⇄ doc ⇄ fixtures.
 */
@NullMarked
public final class SupportedMappings {

    private static final Map<SourceId, List<MappingRow>> BY_SOURCE =
            Map.of(SourceId.of("essentialsx"), EssentialsXMappings.rows());

    private SupportedMappings() {}

    /** The source ids that contribute a mapping table — the built sources (§1.2). */
    public static java.util.Set<SourceId> builtSources() {
        return BY_SOURCE.keySet();
    }

    /** The mapping rows for {@code source}, or an empty list when the source contributes none. */
    public static List<MappingRow> rows(SourceId source) {
        Objects.requireNonNull(source, "source");
        return BY_SOURCE.getOrDefault(source, List.of());
    }
}
