package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationIpBans.MODERATION_IP_BANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationJails.MODERATION_JAILS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationMutes.MODERATION_MUTES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationSeen.MODERATION_SEEN;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationTempbans.MODERATION_TEMPBANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationWarns.MODERATION_WARNS;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link ModerationRepository} over the generated V5 sanction tables. Each sanction kind is
 * its own table; a per-kind {@code save} upserts (delete-then-insert keyed by target) or, for the
 * {@code None} state, deletes the row, so {@code /unmute} and an expired-mute sweep leave no trace. The
 * {@link ModerationProfile} read rebuilds mute/jail/tempban in three keyed selects. Every statement is typed
 * jOOQ DSL — no SQL is ever string-concatenated — and writes go through the transactional {@code write} seam
 * so an offline-row upsert and the dependent write share one transaction.
 */
@NullMarked
public final class JooqModerationRepository extends JooqRepository implements ModerationRepository {

    public JooqModerationRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public ModerationProfile load(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return new ModerationProfile(target, loadMute(target), loadJail(target), loadTempban(target));
    }

    @Override
    public MuteState loadMute(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return read(dsl -> Optional.ofNullable(dsl.selectFrom(MODERATION_MUTES)
                        .where(MODERATION_MUTES.TARGET.eq(target.uuid().toString()))
                        .fetchOne())
                .map(ModerationRows::toMute)
                .orElseGet(MuteState::none));
    }

    @Override
    public JailState loadJail(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return read(dsl -> Optional.ofNullable(dsl.selectFrom(MODERATION_JAILS)
                        .where(MODERATION_JAILS.TARGET.eq(target.uuid().toString()))
                        .fetchOne())
                .map(ModerationRows::toJail)
                .orElseGet(JailState::none));
    }

    @Override
    public TempbanState loadTempban(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return read(dsl -> Optional.ofNullable(dsl.selectFrom(MODERATION_TEMPBANS)
                        .where(MODERATION_TEMPBANS.TARGET.eq(target.uuid().toString()))
                        .fetchOne())
                .map(ModerationRows::toTempban)
                .orElseGet(TempbanState::none));
    }

    @Override
    public void saveMute(PlayerRef target, MuteState mute) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mute, "mute");
        write(dsl -> ModerationWrites.saveMute(dsl, target, mute));
    }

    @Override
    public void saveJail(PlayerRef target, JailState jail) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(jail, "jail");
        write(dsl -> ModerationWrites.saveJail(dsl, target, jail));
    }

    @Override
    public void saveTempban(PlayerRef target, TempbanState tempban) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(tempban, "tempban");
        write(dsl -> ModerationWrites.saveTempban(dsl, target, tempban));
    }

    @Override
    public int appendWarn(PlayerRef target, Warn warn) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(warn, "warn");
        return write(dsl -> ModerationWrites.appendWarn(dsl, target, warn));
    }

    @Override
    public List<Warn> warns(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return read(dsl -> dsl.selectFrom(MODERATION_WARNS)
                .where(MODERATION_WARNS.TARGET.eq(target.uuid().toString()))
                .orderBy(MODERATION_WARNS.TS.desc(), MODERATION_WARNS.ID.desc())
                .fetch()
                .map(ModerationRows::toWarn));
    }

    @Override
    public void saveIpBan(IpBan ban) {
        Objects.requireNonNull(ban, "ban");
        write(dsl -> ModerationWrites.saveIpBan(dsl, ban));
    }

    @Override
    public boolean removeIpBan(String ip) {
        Objects.requireNonNull(ip, "ip");
        return write(dsl -> dsl.deleteFrom(MODERATION_IP_BANS)
                        .where(MODERATION_IP_BANS.IP.eq(ip))
                        .execute())
                > 0;
    }

    @Override
    public Optional<IpBan> activeIpBan(String ip, Instant now) {
        Objects.requireNonNull(ip, "ip");
        Objects.requireNonNull(now, "now");
        return read(dsl -> Optional.ofNullable(dsl.selectFrom(MODERATION_IP_BANS)
                        .where(MODERATION_IP_BANS.IP.eq(ip))
                        .fetchOne())
                .map(ModerationRows.IpBanMapper::toIpBan)
                .filter(ban -> ban.isActiveAt(now)));
    }

    @Override
    public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(ip, "ip");
        Objects.requireNonNull(at, "at");
        write(dsl -> ModerationWrites.recordSeen(dsl, who, ip.orElse(null), at));
    }

    @Override
    public Optional<SeenRecord> seen(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return read(dsl -> Optional.ofNullable(dsl.selectFrom(MODERATION_SEEN)
                        .where(MODERATION_SEEN.UUID.eq(who.uuid().toString()))
                        .fetchOne())
                .map(ModerationRows.SeenMapper::toSeen));
    }

    @Override
    public List<UUID> altsByIp(String ip, UUID self) {
        Objects.requireNonNull(ip, "ip");
        Objects.requireNonNull(self, "self");
        return read(dsl -> dsl.select(MODERATION_SEEN.UUID)
                .from(MODERATION_SEEN)
                .where(MODERATION_SEEN.LAST_IP.eq(ip))
                .and(MODERATION_SEEN.UUID.ne(self.toString()))
                .fetch(MODERATION_SEEN.UUID, UUID.class));
    }

    @Override
    public void ensureUserExists(PlayerRef target, Instant at) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(at, "at");
        write(dsl -> ModerationWrites.ensureSeenRow(dsl, target, at));
    }
}
