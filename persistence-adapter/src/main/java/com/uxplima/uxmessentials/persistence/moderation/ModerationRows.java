package com.uxplima.uxmessentials.persistence.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationIpBansRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationJailsRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationMutesRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationSeenRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationTempbansRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationWarnsRecord;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between the moderation sanction rows and the domain value objects. UUIDs are
 * the canonical 36-character text and instants epoch milliseconds, identical on every backend; this class is
 * the single place that translation lives, so the repository stays a sequence of typed jOOQ statements.
 *
 * <p>A {@code null} {@code until} on a mute or jail row rebuilds to the permanent shape; an online-only jail
 * is identified by a present {@code remaining_millis} (mutually exclusive with {@code until} per the schema).
 * The issuer's UUID is nullable (a console actor) but its name is always present.
 */
final class ModerationRows {

    private ModerationRows() {}

    static MuteState toMute(ModerationMutesRecord row) {
        Issuer issuer = issuer(row.getMutedBy(), row.getMutedByName());
        Optional<String> reason = Optional.ofNullable(row.getReason());
        Instant issuedAt = Instant.ofEpochMilli(row.getCreatedAt());
        Long until = row.getUntil();
        return until == null
                ? MuteState.permanent(issuer, reason, issuedAt)
                : MuteState.timed(Instant.ofEpochMilli(until), issuer, reason, issuedAt);
    }

    static JailState toJail(ModerationJailsRecord row) {
        Issuer issuer = issuer(row.getJailedBy(), row.getJailedByName());
        Optional<String> reason = Optional.ofNullable(row.getReason());
        Instant issuedAt = Instant.ofEpochMilli(row.getCreatedAt());
        Long until = row.getUntil();
        Long remaining = row.getRemainingMillis();
        if (until != null) {
            return JailState.wallClockTimed(row.getJail(), Instant.ofEpochMilli(until), issuer, reason, issuedAt);
        }
        if (remaining != null) {
            return JailState.onlineTimed(row.getJail(), Duration.ofMillis(remaining), issuer, reason, issuedAt);
        }
        return JailState.permanent(row.getJail(), issuer, reason, issuedAt);
    }

    static TempbanState toTempban(ModerationTempbansRecord row) {
        Issuer issuer = issuer(row.getBannedBy(), row.getBannedByName());
        return TempbanState.active(
                Instant.ofEpochMilli(row.getUntil()),
                issuer,
                Optional.ofNullable(row.getReason()),
                Instant.ofEpochMilli(row.getCreatedAt()));
    }

    static BanEntry toBanEntry(ModerationTempbansRecord row) {
        return new BanEntry(
                UUID.fromString(row.getTarget()),
                issuer(row.getBannedBy(), row.getBannedByName()),
                Optional.ofNullable(row.getReason()),
                Instant.ofEpochMilli(row.getUntil()));
    }

    static JailEntry toJailEntry(ModerationJailsRecord row) {
        return new JailEntry(
                UUID.fromString(row.getTarget()),
                row.getJail(),
                issuer(row.getJailedBy(), row.getJailedByName()),
                Optional.ofNullable(row.getReason()),
                Optional.ofNullable(row.getUntil()).map(Instant::ofEpochMilli),
                Optional.ofNullable(row.getRemainingMillis()).map(Duration::ofMillis));
    }

    static MuteEntry toMuteEntry(ModerationMutesRecord row) {
        Long until = row.getUntil();
        return new MuteEntry(
                UUID.fromString(row.getTarget()),
                issuer(row.getMutedBy(), row.getMutedByName()),
                Optional.ofNullable(row.getReason()),
                Optional.ofNullable(until).map(Instant::ofEpochMilli));
    }

    static Warn toWarn(ModerationWarnsRecord row) {
        return new Warn(
                issuer(row.getWarnedBy(), row.getWarnedByName()),
                Optional.ofNullable(row.getReason()),
                Instant.ofEpochMilli(row.getTs()),
                Optional.ofNullable(row.getExpiresAt()).map(Instant::ofEpochMilli));
    }

    private static Issuer issuer(@Nullable String uuid, String name) {
        return Issuer.stored(Optional.ofNullable(uuid).map(UUID::fromString), name);
    }

    /** Maps an {@code moderation_ip_bans} row to a domain {@link IpBan}. */
    static final class IpBanMapper {

        private IpBanMapper() {}

        static IpBan toIpBan(ModerationIpBansRecord row) {
            return new IpBan(
                    row.getIp(),
                    Optional.ofNullable(row.getUntil()).map(Instant::ofEpochMilli),
                    Optional.ofNullable(row.getReason()),
                    Optional.ofNullable(row.getTarget()).map(UUID::fromString),
                    issuer(row.getBannedBy(), row.getBannedByName()),
                    Instant.ofEpochMilli(row.getCreatedAt()));
        }
    }

    /** Maps a {@code moderation_seen} row to a domain {@link SeenRecord}. */
    static final class SeenMapper {

        private SeenMapper() {}

        static SeenRecord toSeen(ModerationSeenRecord row) {
            return new SeenRecord(
                    new PlayerRef(UUID.fromString(row.getUuid()), row.getName()),
                    Optional.ofNullable(row.getLastIp()),
                    Instant.ofEpochMilli(row.getFirstSeen()),
                    Instant.ofEpochMilli(row.getLastSeen()));
        }
    }
}
