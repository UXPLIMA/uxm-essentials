package com.uxplima.uxmessentials.migration;

import static com.uxplima.uxmessentials.migration.Fixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.essentialsx.EssentialsXConvert;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.migration.convert.map.ImportedWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldBlacklist;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/**
 * An import holds a warp to its module's {@code world-blacklist}: a warp in a barred world is skipped, named on its
 * audit line, and counted, by a dry run as by a live one.
 */
class BlacklistedWorldSkipTest {

    private static final Instant CREATED = Instant.parse("2025-01-01T00:00:00Z");
    private static final WorldRef ARENA =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000c001"), "arena");
    private static final WorldBlacklist NO_ARENA = WorldBlacklist.of(List.of("arena"));
    private static final ImportOptions LIVE = ImportOptions.live(Path.of("source"));

    private final List<ImportRecord> reached = Collections.synchronizedList(new ArrayList<>());
    private final RecordWriter delegate = (rec, options) -> {
        reached.add(rec);
        return RecordResult.of(RecordOutcome.WRITTEN);
    };

    private static ImportRecord serverWarp(String name, WorldRef world) {
        PlayerRef owner = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), "Owner");
        return new ImportRecord.WarpRecord(new ImportedWarp(
                Warp.create(WarpName.of(name), new Position(world, 1, 64, 1, 0f, 0f), owner, CREATED)));
    }

    private static ImportRecord playerWarp(String name, WorldRef world) {
        return new ImportRecord.PlayerWarpRecord(new ImportedPlayerWarp(
                UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
                Optional.of("Owner"),
                name,
                Optional.of(name),
                new Position(world, 1, 64, 1, 0f, 0f),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                WarpAccess.PUBLIC,
                Optional.empty(),
                BigDecimal.ZERO,
                "default",
                BigDecimal.ZERO,
                CREATED,
                0L,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    }

    @Test
    void aServerWarpInABarredWorldIsSkippedAndNamedWithoutBeingWritten() {
        RecordWriter writer = new BlacklistedWorldSkip(delegate, () -> NO_ARENA, WorldBlacklist::none);

        RecordResult result = writer.write(serverWarp("pit", ARENA), LIVE);

        assertThat(result.outcome()).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(result.notes()).containsExactly("held_back=world_blacklist", "world=arena");
        assertThat(reached).isEmpty();
    }

    @Test
    void aPlayerWarpInABarredWorldIsSkippedAndNamedWithoutBeingWritten() {
        RecordWriter writer = new BlacklistedWorldSkip(delegate, WorldBlacklist::none, () -> NO_ARENA);

        RecordResult result = writer.write(playerWarp("pit", ARENA), LIVE);

        assertThat(result.outcome()).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(result.notes()).containsExactly("held_back=world_blacklist", "world=arena");
        assertThat(reached).isEmpty();
    }

    @Test
    void eachKindIsHeldOnlyToItsOwnModulesList() {
        RecordWriter serverWarpsBarred = new BlacklistedWorldSkip(delegate, () -> NO_ARENA, WorldBlacklist::none);
        RecordWriter playerWarpsBarred = new BlacklistedWorldSkip(delegate, WorldBlacklist::none, () -> NO_ARENA);

        assertThat(serverWarpsBarred.write(playerWarp("pit", ARENA), LIVE).outcome())
                .isEqualTo(RecordOutcome.WRITTEN);
        assertThat(playerWarpsBarred.write(serverWarp("pit", ARENA), LIVE).outcome())
                .isEqualTo(RecordOutcome.WRITTEN);
        assertThat(reached).hasSize(2);
    }

    @Test
    void aWarpInAnotherWorldGoesOnToTheWriter() {
        RecordWriter writer = new BlacklistedWorldSkip(delegate, () -> NO_ARENA, () -> NO_ARENA);

        assertThat(writer.write(serverWarp("market", WORLD), LIVE).outcome()).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(writer.write(playerWarp("farm", WORLD), LIVE).outcome()).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(reached).hasSize(2);
    }

    @Test
    void anEssentialsXDryRunCountsEveryWarpInABarredWorldAsSkipped() {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        ImportData importData = new ImportData(new LineAudit(lines), () -> "backup-1", new RecordingLogger());
        RecordWriter dryRun = new BlacklistedWorldSkip(
                (rec, options) -> RecordResult.of(RecordOutcome.WRITTEN),
                () -> WorldBlacklist.of(List.of(WORLD.name())),
                WorldBlacklist::none);

        ImportSummary summary = importData.run(
                "console",
                new EssentialsXConvert(Fixtures.ONLY_WORLD, new RecordingLogger()),
                ImportOptions.live(Fixtures.path("/essentialsx")).asDryRun(),
                dryRun);

        assertThat(summary.warps()).isPositive();
        assertThat(summary.skipped()).isEqualTo(summary.warps());
        assertThat(lines)
                .filteredOn(line -> line.startsWith("warp "))
                .hasSize((int) summary.warps())
                .allMatch(line -> line.endsWith("skipped held_back=world_blacklist world=world"));
    }

    /** Keeps each record's kind, outcome and notes, the parts of the audit line this skip writes. */
    private record LineAudit(List<String> lines) implements MigrationAudit {

        @Override
        public void start(
                String actor,
                com.uxplima.uxmessentials.migration.convert.SourceId source,
                ImportMode mode,
                String sourcePath,
                String backup) {}

        @Override
        public void record(
                com.uxplima.uxmessentials.migration.convert.SourceId source, ImportRecord rec, RecordResult result) {
            lines.add(rec.kind() + " " + result.outcome().token() + " " + String.join(" ", result.notes()));
        }

        @Override
        public void finish(String actor, ImportSummary summary, java.time.Duration elapsed) {}
    }
}
