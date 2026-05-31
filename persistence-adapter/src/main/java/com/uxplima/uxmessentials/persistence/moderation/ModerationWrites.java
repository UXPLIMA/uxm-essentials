package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationIpBans.MODERATION_IP_BANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationJails.MODERATION_JAILS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationMutes.MODERATION_MUTES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationSeen.MODERATION_SEEN;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationTempbans.MODERATION_TEMPBANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationWarns.MODERATION_WARNS;

import java.time.Instant;

import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

/**
 * The mutating side of the moderation repository, kept out of the repository class so each save stays a short
 * focused unit and the repository reads as a table of port methods. Every method runs inside the
 * transactional {@code DSLContext} the repository's {@code write} seam hands in, so an offline-row upsert and
 * the dependent sanction write commit or roll back together.
 *
 * <p>Each {@code save} is a delete-then-insert keyed by target: a {@code None} state leaves only the delete
 * (so {@code /unmute} clears the row), and a present state re-inserts the single keyed row. This is portable
 * across SQLite/MySQL/PostgreSQL without a dialect-specific {@code ON CONFLICT}/{@code ON DUPLICATE KEY}.
 */
final class ModerationWrites {

    private ModerationWrites() {}

    static int saveMute(DSLContext dsl, PlayerRef target, MuteState mute) {
        String key = target.uuid().toString();
        dsl.deleteFrom(MODERATION_MUTES).where(MODERATION_MUTES.TARGET.eq(key)).execute();
        if (mute instanceof MuteState.Permanent perm) {
            return insertMute(dsl, key, null, perm.issuer(), perm.reason().orElse(null), perm.issuedAt());
        }
        if (mute instanceof MuteState.Timed timed) {
            return insertMute(
                    dsl,
                    key,
                    timed.until().toEpochMilli(),
                    timed.issuer(),
                    timed.reason().orElse(null),
                    timed.issuedAt());
        }
        return 0;
    }

    private static int insertMute(
            DSLContext dsl,
            String key,
            @Nullable Long until,
            Issuer issuer,
            @Nullable String reason,
            Instant issuedAt) {
        return dsl.insertInto(MODERATION_MUTES)
                .set(MODERATION_MUTES.TARGET, key)
                .set(MODERATION_MUTES.UNTIL, until)
                .set(MODERATION_MUTES.REASON, reason)
                .set(MODERATION_MUTES.MUTED_BY, issuerUuid(issuer))
                .set(MODERATION_MUTES.MUTED_BY_NAME, issuer.name())
                .set(MODERATION_MUTES.CREATED_AT, issuedAt.toEpochMilli())
                .execute();
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
                .set(MODERATION_JAILS.JAILED_BY_NAME, active.issuer().name())
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
        return dsl.insertInto(MODERATION_TEMPBANS)
                .set(MODERATION_TEMPBANS.TARGET, key)
                .set(MODERATION_TEMPBANS.UNTIL, active.until().toEpochMilli())
                .set(MODERATION_TEMPBANS.REASON, active.reason().orElse(null))
                .set(MODERATION_TEMPBANS.BANNED_BY, issuerUuid(active.issuer()))
                .set(MODERATION_TEMPBANS.BANNED_BY_NAME, active.issuer().name())
                .set(MODERATION_TEMPBANS.CREATED_AT, active.issuedAt().toEpochMilli())
                .execute();
    }

    static int appendWarn(DSLContext dsl, PlayerRef target, Warn warn) {
        long nextId = nextWarnId(dsl);
        dsl.insertInto(MODERATION_WARNS)
                .set(MODERATION_WARNS.ID, nextId)
                .set(MODERATION_WARNS.TARGET, target.uuid().toString())
                .set(MODERATION_WARNS.REASON, warn.reason().orElse(null))
                .set(MODERATION_WARNS.WARNED_BY, issuerUuid(warn.issuer()))
                .set(MODERATION_WARNS.WARNED_BY_NAME, warn.issuer().name())
                .set(MODERATION_WARNS.TS, warn.issuedAt().toEpochMilli())
                .execute();
        return dsl.fetchCount(
                MODERATION_WARNS, MODERATION_WARNS.TARGET.eq(target.uuid().toString()));
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
        return dsl.insertInto(MODERATION_IP_BANS)
                .set(MODERATION_IP_BANS.IP, ban.ip())
                .set(
                        MODERATION_IP_BANS.UNTIL,
                        ban.until().map(Instant::toEpochMilli).orElse(null))
                .set(MODERATION_IP_BANS.REASON, ban.reason().orElse(null))
                .set(
                        MODERATION_IP_BANS.TARGET,
                        ban.target().map(java.util.UUID::toString).orElse(null))
                .set(MODERATION_IP_BANS.BANNED_BY, issuerUuid(ban.issuer()))
                .set(MODERATION_IP_BANS.BANNED_BY_NAME, ban.issuer().name())
                .set(MODERATION_IP_BANS.CREATED_AT, ban.issuedAt().toEpochMilli())
                .execute();
    }

    static int recordSeen(DSLContext dsl, PlayerRef who, @Nullable String ip, Instant at) {
        String key = who.uuid().toString();
        boolean exists = dsl.fetchExists(MODERATION_SEEN, MODERATION_SEEN.UUID.eq(key));
        if (!exists) {
            return insertSeen(dsl, key, who.name(), ip, at);
        }
        var update = dsl.update(MODERATION_SEEN)
                .set(MODERATION_SEEN.NAME, who.name())
                .set(MODERATION_SEEN.LAST_SEEN, at.toEpochMilli());
        if (ip != null) {
            update = update.set(MODERATION_SEEN.LAST_IP, ip);
        }
        return update.where(MODERATION_SEEN.UUID.eq(key)).execute();
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
                .set(MODERATION_SEEN.NAME, name)
                .set(MODERATION_SEEN.LAST_IP, ip)
                .set(MODERATION_SEEN.FIRST_SEEN, at.toEpochMilli())
                .set(MODERATION_SEEN.LAST_SEEN, at.toEpochMilli())
                .execute();
    }

    private static @Nullable String issuerUuid(Issuer issuer) {
        return issuer.uuid().map(java.util.UUID::toString).orElse(null);
    }
}
