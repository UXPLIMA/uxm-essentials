package com.uxplima.uxmessentials.migration;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

import com.uxplima.uxmessentials.migration.convert.SourceId;
import org.jspecify.annotations.NullMarked;

/**
 * The running tally of one import, accumulated across the bounded executor's writers and rendered into
 * the {@code migration_import_finish} audit line (docs/12-migration §9). Counters are {@link LongAdder}s
 * because many writer threads increment them concurrently and only one thread reads the totals at the
 * end (§7). The same summary is produced by a dry run, so an operator can diff a dry run against the real
 * run that follows.
 */
@NullMarked
public final class ImportSummary {

    private final SourceId source;
    private final ImportMode mode;
    private final LongAdder users = new LongAdder();
    private final LongAdder warps = new LongAdder();
    private final LongAdder kits = new LongAdder();
    private final LongAdder jails = new LongAdder();
    private final LongAdder mutes = new LongAdder();
    private final LongAdder bans = new LongAdder();
    private final LongAdder ipBans = new LongAdder();
    private final LongAdder warns = new LongAdder();
    private final LongAdder skipped = new LongAdder();
    private final LongAdder failed = new LongAdder();

    public ImportSummary(SourceId source, ImportMode mode) {
        this.source = java.util.Objects.requireNonNull(source, "source");
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
    }

    /** Fold one record's outcome into the tally, by kind and result. */
    public void record(ImportRecord rec, RecordOutcome outcome) {
        countKind(rec);
        if (outcome == RecordOutcome.SKIPPED) {
            skipped.increment();
        } else if (outcome == RecordOutcome.FAILED) {
            failed.increment();
        }
    }

    private void countKind(ImportRecord rec) {
        switch (rec) {
            case ImportRecord.UserRecord ignored -> users.increment();
            case ImportRecord.WarpRecord ignored -> warps.increment();
            case ImportRecord.KitRecord ignored -> kits.increment();
            case ImportRecord.ModerationRecord moderation -> countModeration(moderation);
            case ImportRecord.BanRecord ignored -> bans.increment();
            case ImportRecord.IpBanRecord ignored -> ipBans.increment();
            case ImportRecord.WarnRecord ignored -> warns.increment();
        }
    }

    private void countModeration(ImportRecord.ModerationRecord rec) {
        // One moderation record can carry both a mute and a jail; each is tallied on its own counter so the
        // finish line's jails= and mutes= fields read independently (docs/12-migration §9).
        if (rec.moderation().hasJail()) {
            jails.increment();
        }
        if (rec.moderation().hasMute()) {
            mutes.increment();
        }
    }

    public SourceId source() {
        return source;
    }

    public boolean dryRun() {
        return mode.isDryRun();
    }

    public long users() {
        return users.sum();
    }

    public long warps() {
        return warps.sum();
    }

    public long kits() {
        return kits.sum();
    }

    public long jails() {
        return jails.sum();
    }

    public long mutes() {
        return mutes.sum();
    }

    public long bans() {
        return bans.sum();
    }

    public long ipBans() {
        return ipBans.sum();
    }

    public long warns() {
        return warns.sum();
    }

    public long skipped() {
        return skipped.sum();
    }

    public long failed() {
        return failed.sum();
    }

    /**
     * The total records the executor handed to the writer, across every kind. A moderation record counts
     * once here (it is one record) even though it feeds two of the finish line's counters.
     */
    public long total() {
        return users() + warps() + kits() + moderation() + bans() + ipBans() + warns();
    }

    private long moderation() {
        // Moderation records are tallied per-sanction on jails/mutes, but only the users carrying a sanction
        // are records; the maximum of the two counters is the moderation-record count (a record sets at least
        // one, possibly both, so neither under- nor over-counts the records themselves).
        return Math.max(jails(), mutes());
    }

    /** A one-line, audit-friendly rendering of the totals plus the run duration. */
    public String describe(Duration elapsed) {
        return "source=" + source + " dry_run=" + dryRun() + " users=" + users() + " warps=" + warps() + " kits="
                + kits() + " jails=" + jails() + " mutes=" + mutes() + " bans=" + bans() + " ip_bans=" + ipBans()
                + " warns=" + warns() + " skipped=" + skipped() + " failed=" + failed() + " duration_ms="
                + elapsed.toMillis();
    }
}
