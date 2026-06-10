package com.uxplima.uxmessentials.migration.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.ImportedModeration;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * The dry-run {@link RecordWriter}: runs the identical conflict-resolution logic against the live
 * repositories (reads only) and reports the outcome each record <em>would</em> get, but writes nothing
 * (docs/12-migration §1, §7). Because the parse, map, and conflict-resolve stages are shared with the
 * live writer, a dry run's per-record and summary audit are the lines a live run would emit, so an
 * operator can review conflicts and counts before committing.
 */
@NullMarked
public final class DryRunRecordWriter implements RecordWriter {

    private final WarpRepository warps;
    private final ModerationRepository moderation;
    private final KitRepository kits;

    public DryRunRecordWriter(WarpRepository warps, ModerationRepository moderation, KitRepository kits) {
        this.warps = Objects.requireNonNull(warps, "warps");
        this.moderation = Objects.requireNonNull(moderation, "moderation");
        this.kits = Objects.requireNonNull(kits, "kits");
    }

    @Override
    public RecordOutcome write(ImportRecord record, ImportOptions options) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(options, "options");
        return switch (record) {
            case ImportRecord.UserRecord ignored -> RecordOutcome.WRITTEN;
            case ImportRecord.WarpRecord warp -> warpOutcome(warp.warp().warp().name(), options);
            case ImportRecord.KitRecord kit -> kitOutcome(kit.kit().definition().id(), options);
            case ImportRecord.ModerationRecord rec -> moderationOutcome(rec.moderation(), options);
        };
    }

    private RecordOutcome warpOutcome(WarpName name, ImportOptions options) {
        boolean existed = warps.exists(name);
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    private RecordOutcome kitOutcome(KitId id, ImportOptions options) {
        boolean existed = kits.exists(id);
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    private RecordOutcome moderationOutcome(ImportedModeration imported, ImportOptions options) {
        boolean existed = isSanctioned(moderation.load(imported.profile().target()));
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    private static boolean isSanctioned(ModerationProfile existing) {
        return !(existing.mute() instanceof MuteState.None) || !(existing.jail() instanceof JailState.None);
    }
}
