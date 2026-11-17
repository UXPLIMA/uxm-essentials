package com.uxplima.uxmessentials.migration.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.ImportSummary;
import com.uxplima.uxmessentials.migration.MigrationAudit;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordResult;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Writes the importer's audit trail to the dedicated {@code com.uxplima.uxmessentials.audit} channel as
 * structured {@code event=migration_import_* key=value} lines. The {@code start → per-record → finish} family makes any run fully replayable;
 * a dry run emits the identical lines with {@code dry_run=true} so an operator can diff it against the
 * real run that follows. Audit lines are operator-facing, so they go through a {@link Logger} bound to
 * the audit channel, never the player-facing {@code MessageKey} catalogue.
 */
@NullMarked
public final class AuditChannelMigrationAudit implements MigrationAudit {

    private final Logger audit;

    public AuditChannelMigrationAudit(Logger audit) {
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public void start(String actor, SourceId source, ImportMode mode, String sourcePath, String backup) {
        audit.info(
                "event=migration_import_start actor={} source={} dry_run={} source_path={} backup={}",
                actor,
                source,
                mode.isDryRun(),
                sourcePath,
                backup);
    }

    @Override
    public void record(SourceId source, ImportRecord rec, RecordResult result) {
        RecordOutcome outcome = result.outcome();
        switch (rec) {
            case ImportRecord.AccountRecord account -> recordAccount(source, account, outcome);
            case ImportRecord.UserRecord user -> recordUser(source, user, result);
            case ImportRecord.WarpRecord warp -> recordWarp(source, warp, result);
            case ImportRecord.PlayerWarpRecord warp -> recordPlayerWarp(source, warp, result);
            case ImportRecord.KitRecord kit -> recordKit(source, kit, outcome);
            case ImportRecord.ModerationRecord moderation -> recordModeration(source, moderation, outcome);
            case ImportRecord.BanRecord ban -> recordBan(source, ban, outcome);
            case ImportRecord.IpBanRecord ban -> recordIpBan(source, ban, outcome);
            case ImportRecord.WarnRecord warn -> recordWarn(source, warn, outcome);
            case ImportRecord.HologramRecord hologram -> recordHologram(source, hologram, outcome);
            case ImportRecord.WorldRecord world -> recordWorld(source, world, outcome);
        }
    }

    // skipped means the name index already held the account, from a join on a server sharing this database.
    private void recordAccount(SourceId source, ImportRecord.AccountRecord rec, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_account source={} uuid={} name={} conflict={} ok={}",
                source,
                rec.account().uuid(),
                rec.account().name(),
                outcome.token(),
                outcome.ok());
    }

    private void recordWorld(SourceId source, ImportRecord.WorldRecord rec, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_world source={} world={} alias={} environment={} settings={} conflict={} ok={}",
                source,
                rec.world().world().name().value(),
                rec.world().world().alias().orElse(""),
                rec.world().world().spec().environment(),
                rec.world().world().settings().raw().size(),
                outcome.token(),
                outcome.ok());
    }

    private void recordHologram(SourceId source, ImportRecord.HologramRecord rec, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_hologram source={} hologram={} lines={} conflict={} ok={}",
                source,
                rec.hologram().hologram().name().value(),
                rec.hologram().hologram().lines().size(),
                outcome.token(),
                outcome.ok());
    }

    private void recordUser(SourceId source, ImportRecord.UserRecord rec, RecordResult result) {
        // The counts before conflict= are what the source held; the notes after ok= say which parts landed.
        audit.info(
                "event=migration_import_user source={} uuid={} homes={} balance_imported={} mailbox={} conflict={} ok={}{}",
                source,
                rec.user().owner().uuid(),
                rec.user().homes().size(),
                rec.user().balance().isPresent(),
                rec.user().mail().size(),
                result.outcome().token(),
                result.outcome().ok(),
                notes(result));
    }

    private static String notes(RecordResult result) {
        return result.notes().isEmpty() ? "" : " " + String.join(" ", result.notes());
    }

    // The notes after ok= say why a warp was held back, such as a world on world-blacklist.
    private void recordPlayerWarp(SourceId source, ImportRecord.PlayerWarpRecord rec, RecordResult result) {
        audit.info(
                "event=migration_import_player_warp source={} warp={} owner={} conflict={} ok={}{}",
                source,
                rec.warp().name(),
                rec.warp().owner(),
                result.outcome().token(),
                result.outcome().ok(),
                notes(result));
    }

    private void recordWarp(SourceId source, ImportRecord.WarpRecord rec, RecordResult result) {
        audit.info(
                "event=migration_import_warp source={} warp={} conflict={} ok={}{}",
                source,
                rec.warp().warp().name().value(),
                result.outcome().token(),
                result.outcome().ok(),
                notes(result));
    }

    private void recordKit(SourceId source, ImportRecord.KitRecord rec, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_kit source={} kit={} conflict={} ok={}",
                source,
                rec.kit().definition().id().value(),
                outcome.token(),
                outcome.ok());
    }

    private void recordModeration(SourceId source, ImportRecord.ModerationRecord rec, RecordOutcome outcome) {
        // One moderation record can carry both sanctions; the doc catalogue keeps jail and mute as separate
        // events, so each present sanction emits its own audit line.
        ImportedModeration imported = rec.moderation();
        ModerationProfile profile = imported.profile();
        PlayerRef target = profile.target();
        if (imported.hasJail()) {
            recordJail(source, target, profile, outcome);
        }
        if (imported.hasMute()) {
            recordMute(source, target, profile, outcome);
        }
    }

    private void recordJail(SourceId source, PlayerRef target, ModerationProfile profile, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_jail source={} uuid={} until={} ok={}",
                source,
                target.uuid(),
                jailUntil(profile),
                outcome.ok());
    }

    private void recordMute(SourceId source, PlayerRef target, ModerationProfile profile, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_mute source={} uuid={} until={} ok={}",
                source,
                target.uuid(),
                until(profile.mute().expiry()),
                outcome.ok());
    }

    private void recordBan(SourceId source, ImportRecord.BanRecord rec, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_ban source={} uuid={} until={} conflict={} ok={}",
                source,
                rec.target().uuid(),
                until(banEnd(rec.ban())),
                outcome.token(),
                outcome.ok());
    }

    private void recordIpBan(SourceId source, ImportRecord.IpBanRecord rec, RecordOutcome outcome) {
        IpBan ban = rec.ban();
        audit.info(
                "event=migration_import_ip_ban source={} ip={} target={} until={} conflict={} ok={}",
                source,
                ban.ip(),
                ban.target().map(java.util.UUID::toString).orElse("none"),
                until(ban.until()),
                outcome.token(),
                outcome.ok());
    }

    private void recordWarn(SourceId source, ImportRecord.WarnRecord rec, RecordOutcome outcome) {
        Warn warn = rec.warn();
        audit.info(
                "event=migration_import_warn source={} uuid={} issued_at={} expires_at={} conflict={} ok={}",
                source,
                rec.target().uuid(),
                warn.issuedAt(),
                until(warn.expiresAt()),
                outcome.token(),
                outcome.ok());
    }

    private static String jailUntil(ModerationProfile profile) {
        return profile.jail() instanceof JailState.Active active ? until(active.until()) : "permanent";
    }

    /** When an imported ban ends, empty for a permanent one rather than its far-future stored expiry. */
    private static Optional<Instant> banEnd(TempbanState ban) {
        return ban instanceof TempbanState.Active active ? Ban.endOf(active) : ban.expiry();
    }

    private static String until(Optional<Instant> expiry) {
        return expiry.map(Instant::toString).orElse("permanent");
    }

    @Override
    public void finish(String actor, ImportSummary summary, Duration elapsed) {
        audit.info(
                "event=migration_import_finish actor={} source={} dry_run={} accounts={} users={} warps={} kits={} jails={} mutes={} bans={} ip_bans={} warns={} skipped={} partial={} failed={} duration_ms={}",
                actor,
                summary.source(),
                summary.dryRun(),
                summary.accounts(),
                summary.users(),
                summary.warps(),
                summary.kits(),
                summary.jails(),
                summary.mutes(),
                summary.bans(),
                summary.ipBans(),
                summary.warns(),
                summary.skipped(),
                summary.partial(),
                summary.failed(),
                elapsed.toMillis());
    }
}
