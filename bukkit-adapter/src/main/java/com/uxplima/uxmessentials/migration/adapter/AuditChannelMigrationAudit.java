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
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Writes the importer's audit trail to the dedicated {@code com.uxplima.uxmessentials.audit} channel as
 * structured {@code event=migration_import_* key=value} lines (docs/12-migration §9, docs/09-deployment
 * audit catalogue). The {@code start → per-record → finish} family makes any run fully replayable;
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
    public void record(SourceId source, ImportRecord rec, RecordOutcome outcome) {
        switch (rec) {
            case ImportRecord.UserRecord user -> recordUser(source, user, outcome);
            case ImportRecord.WarpRecord warp -> recordWarp(source, warp, outcome);
            case ImportRecord.PlayerWarpRecord warp -> recordPlayerWarp(source, warp, outcome);
            case ImportRecord.KitRecord kit -> recordKit(source, kit, outcome);
            case ImportRecord.ModerationRecord moderation -> recordModeration(source, moderation, outcome);
            case ImportRecord.BanRecord ban -> recordBan(source, ban, outcome);
            case ImportRecord.IpBanRecord ban -> recordIpBan(source, ban, outcome);
            case ImportRecord.WarnRecord warn -> recordWarn(source, warn, outcome);
            case ImportRecord.HologramRecord hologram -> recordHologram(source, hologram, outcome);
            case ImportRecord.WorldRecord world -> recordWorld(source, world, outcome);
        }
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

    private void recordUser(SourceId source, ImportRecord.UserRecord rec, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_user source={} uuid={} homes={} balance_imported={} mailbox={} conflict={} ok={}",
                source,
                rec.user().owner().uuid(),
                rec.user().homes().size(),
                rec.user().balance().isPresent(),
                rec.user().mail().size(),
                outcome.token(),
                outcome.ok());
    }

    private void recordPlayerWarp(SourceId source, ImportRecord.PlayerWarpRecord rec, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_player_warp source={} warp={} owner={} conflict={} ok={}",
                source,
                rec.warp().name(),
                rec.warp().owner(),
                outcome.token(),
                outcome.ok());
    }

    private void recordWarp(SourceId source, ImportRecord.WarpRecord rec, RecordOutcome outcome) {
        audit.info(
                "event=migration_import_warp source={} warp={} conflict={} ok={}",
                source,
                rec.warp().warp().name().value(),
                outcome.token(),
                outcome.ok());
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
        // events (docs/12-migration §9), so each present sanction emits its own audit line.
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
                until(rec.ban().expiry()),
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

    private static String until(Optional<Instant> expiry) {
        return expiry.map(Instant::toString).orElse("permanent");
    }

    @Override
    public void finish(String actor, ImportSummary summary, Duration elapsed) {
        audit.info(
                "event=migration_import_finish actor={} source={} dry_run={} users={} warps={} kits={} jails={} mutes={} bans={} ip_bans={} warns={} skipped={} failed={} duration_ms={}",
                actor,
                summary.source(),
                summary.dryRun(),
                summary.users(),
                summary.warps(),
                summary.kits(),
                summary.jails(),
                summary.mutes(),
                summary.bans(),
                summary.ipBans(),
                summary.warns(),
                summary.skipped(),
                summary.failed(),
                elapsed.toMillis());
    }
}
