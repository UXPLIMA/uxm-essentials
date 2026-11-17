package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationIpBans.MODERATION_IP_BANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationJails.MODERATION_JAILS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationMutes.MODERATION_MUTES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationSeen.MODERATION_SEEN;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationTempbans.MODERATION_TEMPBANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationWarns.MODERATION_WARNS;
import static com.uxplima.uxmessentials.persistence.runtime.ColumnFit.fit;

import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.DrawnReason;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationIpBansRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationMutesRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationTempbansRecord;
import com.uxplima.uxmessentials.persistence.runtime.ColumnFit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

/**
 * The mutating side of the moderation repository, kept out of the repository class so each save stays a short
 * focused unit and the repository reads as a table of port methods. Every method runs inside the
 * transactional {@code DSLContext} the repository's {@code write} seam hands in, so an offline-row upsert and
 * the dependent sanction write commit or roll back together.
 *
 * <p>Each {@code save} is a delete-then-insert keyed by target: a {@code None} state leaves only the delete
 * (so {@code /unmute} clears the row), and a present state re-inserts the single keyed row. The conditional ban,
 * mute and IP ban writes are the exception: their insert is jOOQ's {@code ON CONFLICT DO NOTHING}, which jOOQ renders
 * per dialect ({@code INSERT IGNORE} on MySQL/MariaDB), so the database itself refuses a row that appeared since the
 * read.
 */
final class ModerationWrites {

    private ModerationWrites() {}

    static int saveMute(DSLContext dsl, PlayerRef target, MuteState mute) {
        String key = target.uuid().toString();
        dsl.deleteFrom(MODERATION_MUTES).where(MODERATION_MUTES.TARGET.eq(key)).execute();
        if (mute instanceof MuteState.None) {
            return 0;
        }
        return dsl.insertInto(MODERATION_MUTES).set(muteRow(key, mute)).execute();
    }

    /**
     * Write {@code replacement} only while {@code target}'s mute row is still {@code expected}, the same two atomic
     * statements as {@link #replaceTempban}: an update that names the row by its expiry (null for a permanent mute)
     * and issue instant, or an insert that does nothing when a row appeared since. Text columns are fitted first so
     * both branches store the same row on every backend.
     */
    static boolean replaceMute(DSLContext dsl, PlayerRef target, MuteState expected, MuteState replacement) {
        String key = target.uuid().toString();
        ModerationMutesRecord row = muteRow(key, replacement);
        if (!(expected instanceof MuteState.None)) {
            return dsl.update(MODERATION_MUTES)
                            .set(row)
                            .where(MODERATION_MUTES.TARGET.eq(key))
                            .and(nullableEq(MODERATION_MUTES.UNTIL, expected.expiry()))
                            .and(MODERATION_MUTES.CREATED_AT.eq(
                                    issuedAt(expected).toEpochMilli()))
                            .execute()
                    == 1;
        }
        return dsl.insertInto(MODERATION_MUTES)
                        .set(row)
                        .onConflict(MODERATION_MUTES.TARGET)
                        .doNothing()
                        .execute()
                == 1;
    }

    private static ModerationMutesRecord muteRow(String key, MuteState mute) {
        ModerationMutesRecord row = new ModerationMutesRecord();
        row.setTarget(key);
        row.setUntil(mute.expiry().map(Instant::toEpochMilli).orElse(null));
        switch (mute) {
            case MuteState.Permanent permanent ->
                fillIssue(row, permanent.issuer(), permanent.reason(), permanent.drawnReason());
            case MuteState.Timed timed -> fillIssue(row, timed.issuer(), timed.reason(), timed.drawnReason());
            case MuteState.None none -> throw new IllegalArgumentException("a mute row needs a mute");
        }
        row.setCreatedAt(issuedAt(mute).toEpochMilli());
        return row;
    }

    private static void fillIssue(
            ModerationMutesRecord row, Issuer issuer, Optional<String> reason, Optional<DrawnReason> drawn) {
        row.setReason(reason.map(text -> fit(MODERATION_MUTES.REASON, text)).orElse(null));
        row.setReasonKey(ReasonLines.key(drawn));
        row.setReasonArgs(ReasonLines.args(drawn));
        row.setMutedBy(issuerUuid(issuer));
        row.setMutedByName(ColumnFit.name(MODERATION_MUTES.MUTED_BY_NAME, issuer.name()));
    }

    private static Instant issuedAt(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.issuedAt();
            case MuteState.Timed timed -> timed.issuedAt();
            case MuteState.None none -> throw new IllegalArgumentException("no mute, so no issue instant");
        };
    }

    /** {@code column = value}, or {@code column IS NULL} for an empty value, which plain equality never matches. */
    private static Condition nullableEq(Field<Long> column, Optional<Instant> value) {
        return value.map(instant -> column.eq(instant.toEpochMilli())).orElseGet(column::isNull);
    }

    static int saveJail(DSLContext dsl, PlayerRef target, JailState jail) {
        String key = target.uuid().toString();
        dsl.deleteFrom(MODERATION_JAILS).where(MODERATION_JAILS.TARGET.eq(key)).execute();
        if (!(jail instanceof JailState.Active active)) {
            return 0;
        }
        Long until = active.until().map(Instant::toEpochMilli).orElse(null);
        Long remaining = active.remaining().map(java.time.Duration::toMillis).orElse(null);
        return dsl.insertInto(MODERATION_JAILS)
                .set(MODERATION_JAILS.TARGET, key)
                .set(MODERATION_JAILS.JAIL, active.jail())
                .set(MODERATION_JAILS.UNTIL, until)
                .set(MODERATION_JAILS.REMAINING_MILLIS, remaining)
                .set(MODERATION_JAILS.REASON, active.reason().orElse(null))
                .set(MODERATION_JAILS.JAILED_BY, issuerUuid(active.issuer()))
                .set(
                        MODERATION_JAILS.JAILED_BY_NAME,
                        ColumnFit.name(
                                MODERATION_JAILS.JAILED_BY_NAME, active.issuer().name()))
                .set(MODERATION_JAILS.CREATED_AT, active.issuedAt().toEpochMilli())
                .execute();
    }

    static int saveTempban(DSLContext dsl, PlayerRef target, TempbanState tempban) {
        String key = target.uuid().toString();
        dsl.deleteFrom(MODERATION_TEMPBANS)
                .where(MODERATION_TEMPBANS.TARGET.eq(key))
                .execute();
        if (!(tempban instanceof TempbanState.Active active)) {
            return 0;
        }
        return dsl.insertInto(MODERATION_TEMPBANS).set(tempbanRow(key, active)).execute();
    }

    /**
     * Write {@code replacement} only while {@code target}'s row is still {@code expected}. Each branch is one
     * statement that the database checks and applies atomically: the update names the row by its expiry and issue
     * instant, which a ban written in between changes, and the insert does nothing when a row appeared since. On
     * MySQL/MariaDB that insert is {@code INSERT IGNORE}, which also turns a too-long value into a silent truncation
     * instead of an error, so every text column is fitted first and both branches store the same row.
     */
    static boolean replaceTempban(
            DSLContext dsl, PlayerRef target, TempbanState expected, TempbanState.Active replacement) {
        String key = target.uuid().toString();
        ModerationTempbansRecord row = tempbanRow(key, replacement);
        if (expected instanceof TempbanState.Active seen) {
            return dsl.update(MODERATION_TEMPBANS)
                            .set(row)
                            .where(MODERATION_TEMPBANS.TARGET.eq(key))
                            .and(MODERATION_TEMPBANS.UNTIL.eq(seen.until().toEpochMilli()))
                            .and(MODERATION_TEMPBANS.CREATED_AT.eq(
                                    seen.issuedAt().toEpochMilli()))
                            .execute()
                    == 1;
        }
        return dsl.insertInto(MODERATION_TEMPBANS)
                        .set(row)
                        .onConflict(MODERATION_TEMPBANS.TARGET)
                        .doNothing()
                        .execute()
                == 1;
    }

    private static ModerationTempbansRecord tempbanRow(String key, TempbanState.Active ban) {
        ModerationTempbansRecord row = new ModerationTempbansRecord();
        row.setTarget(key);
        row.setUntil(ban.until().toEpochMilli());
        row.setReason(ban.reason()
                .map(reason -> fit(MODERATION_TEMPBANS.REASON, reason))
                .orElse(null));
        row.setReasonKey(ReasonLines.key(ban.drawnReason()));
        row.setReasonArgs(ReasonLines.args(ban.drawnReason()));
        row.setBannedBy(issuerUuid(ban.issuer()));
        row.setBannedByName(
                ColumnFit.name(MODERATION_TEMPBANS.BANNED_BY_NAME, ban.issuer().name()));
        row.setCreatedAt(ban.issuedAt().toEpochMilli());
        return row;
    }

    static int appendWarn(DSLContext dsl, PlayerRef target, Warn warn) {
        long nextId = nextWarnId(dsl);
        Long expiresAt = warn.expiresAt().map(Instant::toEpochMilli).orElse(null);
        dsl.insertInto(MODERATION_WARNS)
                .set(MODERATION_WARNS.ID, nextId)
                .set(MODERATION_WARNS.TARGET, target.uuid().toString())
                .set(MODERATION_WARNS.REASON, warn.reason().orElse(null))
                .set(MODERATION_WARNS.WARNED_BY, issuerUuid(warn.issuer()))
                .set(
                        MODERATION_WARNS.WARNED_BY_NAME,
                        ColumnFit.name(
                                MODERATION_WARNS.WARNED_BY_NAME, warn.issuer().name()))
                .set(MODERATION_WARNS.TS, warn.issuedAt().toEpochMilli())
                .set(MODERATION_WARNS.EXPIRES_AT, expiresAt)
                .execute();
        // The count the actor is told excludes warnings already lapsed at this warning's issue instant; the
        // just-inserted warning is active, so it is always included.
        return dsl.fetchCount(
                MODERATION_WARNS,
                MODERATION_WARNS
                        .TARGET
                        .eq(target.uuid().toString())
                        .and(notLapsed(warn.issuedAt().toEpochMilli())));
    }

    private static org.jooq.Condition notLapsed(long now) {
        return MODERATION_WARNS.EXPIRES_AT.isNull().or(MODERATION_WARNS.EXPIRES_AT.gt(now));
    }

    static int clearWarns(DSLContext dsl, PlayerRef target) {
        String key = target.uuid().toString();
        int removed = dsl.fetchCount(MODERATION_WARNS, MODERATION_WARNS.TARGET.eq(key));
        dsl.deleteFrom(MODERATION_WARNS).where(MODERATION_WARNS.TARGET.eq(key)).execute();
        return removed;
    }

    static int clearWarnsByActor(DSLContext dsl, PlayerRef target, PlayerRef actor) {
        String key = target.uuid().toString();
        String issuer = actor.uuid().toString();
        org.jooq.Condition by = MODERATION_WARNS.TARGET.eq(key).and(MODERATION_WARNS.WARNED_BY.eq(issuer));
        int removed = dsl.fetchCount(MODERATION_WARNS, by);
        dsl.deleteFrom(MODERATION_WARNS).where(by).execute();
        return removed;
    }

    private static long nextWarnId(DSLContext dsl) {
        Long maxId =
                dsl.select(DSL.max(MODERATION_WARNS.ID)).from(MODERATION_WARNS).fetchOne(0, Long.class);
        return (maxId == null ? 0L : maxId) + 1L;
    }

    static int saveIpBan(DSLContext dsl, IpBan ban) {
        dsl.deleteFrom(MODERATION_IP_BANS)
                .where(MODERATION_IP_BANS.IP.eq(ban.ip()))
                .execute();
        return dsl.insertInto(MODERATION_IP_BANS).set(ipBanRow(ban)).execute();
    }

    /**
     * Write {@code replacement} only while its address's row is still {@code expected}, the same two atomic
     * statements as {@link #replaceTempban}: an update that names the row by its expiry (null for a permanent IP ban)
     * and issue instant, or an insert that does nothing when a row appeared since.
     */
    static boolean replaceIpBan(DSLContext dsl, Optional<IpBan> expected, IpBan replacement) {
        ModerationIpBansRecord row = ipBanRow(replacement);
        if (expected.isPresent()) {
            IpBan seen = expected.get();
            return dsl.update(MODERATION_IP_BANS)
                            .set(row)
                            .where(MODERATION_IP_BANS.IP.eq(seen.ip()))
                            .and(nullableEq(MODERATION_IP_BANS.UNTIL, seen.until()))
                            .and(MODERATION_IP_BANS.CREATED_AT.eq(
                                    seen.issuedAt().toEpochMilli()))
                            .execute()
                    == 1;
        }
        return dsl.insertInto(MODERATION_IP_BANS)
                        .set(row)
                        .onConflict(MODERATION_IP_BANS.IP)
                        .doNothing()
                        .execute()
                == 1;
    }

    private static ModerationIpBansRecord ipBanRow(IpBan ban) {
        ModerationIpBansRecord row = new ModerationIpBansRecord();
        row.setIp(ban.ip());
        row.setUntil(ban.until().map(Instant::toEpochMilli).orElse(null));
        row.setReason(
                ban.reason().map(text -> fit(MODERATION_IP_BANS.REASON, text)).orElse(null));
        row.setReasonKey(ReasonLines.key(ban.drawnReason()));
        row.setReasonArgs(ReasonLines.args(ban.drawnReason()));
        row.setTarget(ban.target().map(java.util.UUID::toString).orElse(null));
        row.setBannedBy(issuerUuid(ban.issuer()));
        row.setBannedByName(
                ColumnFit.name(MODERATION_IP_BANS.BANNED_BY_NAME, ban.issuer().name()));
        row.setCreatedAt(ban.issuedAt().toEpochMilli());
        return row;
    }

    static int recordSeen(DSLContext dsl, PlayerRef who, @Nullable String ip, Instant at) {
        String key = who.uuid().toString();
        boolean exists = dsl.fetchExists(MODERATION_SEEN, MODERATION_SEEN.UUID.eq(key));
        if (!exists) {
            return insertSeen(dsl, key, who.name(), ip, at);
        }
        var update = dsl.update(MODERATION_SEEN).set(MODERATION_SEEN.LAST_SEEN, at.toEpochMilli());
        // A uuid standing in for an unknown name never replaces the name a join recorded.
        if (!ColumnFit.isUuid(who.name())) {
            update = update.set(MODERATION_SEEN.NAME, fit(MODERATION_SEEN.NAME, who.name()));
        }
        if (ip != null) {
            update = update.set(MODERATION_SEEN.LAST_IP, ip);
        }
        return update.where(MODERATION_SEEN.UUID.eq(key)).execute();
    }

    /** Advance an existing seen row's last-seen and last address; a missing row stays missing. */
    static int touchSeen(DSLContext dsl, PlayerRef who, @Nullable String ip, Instant at) {
        var update = dsl.update(MODERATION_SEEN).set(MODERATION_SEEN.LAST_SEEN, at.toEpochMilli());
        if (ip != null) {
            update = update.set(MODERATION_SEEN.LAST_IP, ip);
        }
        return update.where(MODERATION_SEEN.UUID.eq(who.uuid().toString())).execute();
    }

    static int ensureSeenRow(DSLContext dsl, PlayerRef target, Instant at) {
        String key = target.uuid().toString();
        if (dsl.fetchExists(MODERATION_SEEN, MODERATION_SEEN.UUID.eq(key))) {
            return 0;
        }
        return insertSeen(dsl, key, target.name(), null, at);
    }

    private static int insertSeen(DSLContext dsl, String key, String name, @Nullable String ip, Instant at) {
        return dsl.insertInto(MODERATION_SEEN)
                .set(MODERATION_SEEN.UUID, key)
                .set(MODERATION_SEEN.NAME, ColumnFit.name(MODERATION_SEEN.NAME, name))
                .set(MODERATION_SEEN.LAST_IP, ip)
                .set(MODERATION_SEEN.FIRST_SEEN, at.toEpochMilli())
                .set(MODERATION_SEEN.LAST_SEEN, at.toEpochMilli())
                .execute();
    }

    private static @Nullable String issuerUuid(Issuer issuer) {
        return issuer.uuid().map(java.util.UUID::toString).orElse(null);
    }
}
