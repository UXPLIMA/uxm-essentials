package com.uxplima.uxmessentials.moderation.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * An in-memory {@link ModerationRepository} for the application tests: per-target maps for each sanction kind,
 * an append-only warn log, an IP ban map and a seen map. A {@code None} save deletes; everything else upserts,
 * mirroring the jOOQ adapter's keyed semantics so a test can assert the same observable behaviour without a DB.
 */
public final class FakeModerationRepository implements ModerationRepository {

    private final Map<UUID, MuteState> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, JailState> jails = new ConcurrentHashMap<>();
    private final Map<UUID, TempbanState> tempbans = new ConcurrentHashMap<>();
    private final Map<UUID, List<Warn>> warns = new ConcurrentHashMap<>();
    private final Map<String, IpBan> ipBans = new ConcurrentHashMap<>();
    private final Map<UUID, SeenRecord> seen = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> ipHistory = new ConcurrentHashMap<>();
    public final List<UUID> ensured = new ArrayList<>();
    private volatile boolean lockedDown;

    @Override
    public ModerationProfile load(PlayerRef target) {
        return new ModerationProfile(target, loadMute(target), loadJail(target), loadTempban(target));
    }

    @Override
    public MuteState loadMute(PlayerRef target) {
        return mutes.getOrDefault(target.uuid(), MuteState.none());
    }

    @Override
    public JailState loadJail(PlayerRef target) {
        return jails.getOrDefault(target.uuid(), JailState.none());
    }

    @Override
    public TempbanState loadTempban(PlayerRef target) {
        return tempbans.getOrDefault(target.uuid(), TempbanState.none());
    }

    @Override
    public List<BanEntry> activeBans(Instant now, int limit) {
        return tempbans.entrySet().stream()
                .filter(e -> e.getValue() instanceof TempbanState.Active active && active.isActiveAt(now))
                .map(e -> {
                    TempbanState.Active active = (TempbanState.Active) e.getValue();
                    return new BanEntry(e.getKey(), active.issuer(), active.reason(), active.until());
                })
                .limit(limit)
                .toList();
    }

    @Override
    public List<MuteEntry> activeMutes(Instant now, int limit) {
        return mutes.entrySet().stream()
                .filter(e -> e.getValue().isActiveAt(now))
                .map(e -> muteEntry(e.getKey(), e.getValue()))
                .limit(limit)
                .toList();
    }

    @Override
    public List<JailEntry> activeJails(Instant now, int limit) {
        return jails.entrySet().stream()
                .filter(e -> e.getValue() instanceof JailState.Active active && active.isActiveAt(now))
                .map(e -> jailEntry(e.getKey(), (JailState.Active) e.getValue()))
                .limit(limit)
                .toList();
    }

    private static JailEntry jailEntry(UUID target, JailState.Active jail) {
        return new JailEntry(target, jail.jail(), jail.issuer(), jail.reason(), jail.until(), jail.remaining());
    }

    private static MuteEntry muteEntry(UUID target, MuteState mute) {
        if (mute instanceof MuteState.Permanent permanent) {
            return new MuteEntry(target, permanent.issuer(), permanent.reason(), Optional.empty());
        }
        MuteState.Timed timed = (MuteState.Timed) mute;
        return new MuteEntry(target, timed.issuer(), timed.reason(), Optional.of(timed.until()));
    }

    @Override
    public void saveMute(PlayerRef target, MuteState mute) {
        upsert(mutes, target.uuid(), mute, mute instanceof MuteState.None);
    }

    @Override
    public void saveJail(PlayerRef target, JailState jail) {
        upsert(jails, target.uuid(), jail, jail instanceof JailState.None);
    }

    @Override
    public void saveTempban(PlayerRef target, TempbanState tempban) {
        upsert(tempbans, target.uuid(), tempban, tempban instanceof TempbanState.None);
    }

    @Override
    public int appendWarn(PlayerRef target, Warn warn) {
        List<Warn> list = warns.computeIfAbsent(target.uuid(), uuid -> new ArrayList<>());
        list.add(warn);
        return (int) list.stream().filter(w -> !w.isExpiredAt(warn.issuedAt())).count();
    }

    @Override
    public List<Warn> warns(PlayerRef target, Instant now) {
        List<Warn> list = new ArrayList<>(warns.getOrDefault(target.uuid(), List.of()));
        list.removeIf(warn -> warn.isExpiredAt(now));
        java.util.Collections.reverse(list);
        return list;
    }

    @Override
    public int clearWarns(PlayerRef target) {
        List<Warn> removed = warns.remove(target.uuid());
        return removed == null ? 0 : removed.size();
    }

    @Override
    public void saveIpBan(IpBan ban) {
        ipBans.put(ban.ip(), ban);
    }

    @Override
    public boolean removeIpBan(String ip) {
        return ipBans.remove(ip) != null;
    }

    @Override
    public Optional<IpBan> activeIpBan(String ip, Instant now) {
        return Optional.ofNullable(ipBans.get(ip)).filter(ban -> ban.isActiveAt(now));
    }

    @Override
    public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
        seen.compute(
                who.uuid(),
                (uuid, existing) -> existing == null ? SeenRecord.firstJoin(who, ip, at) : existing.touched(at, ip));
    }

    @Override
    public void recordIpSeen(UUID uuid, String ip, Instant now) {
        ipHistory.computeIfAbsent(uuid, key -> ConcurrentHashMap.newKeySet()).add(ip);
    }

    @Override
    public Optional<SeenRecord> seen(PlayerRef who) {
        return Optional.ofNullable(seen.get(who.uuid()));
    }

    @Override
    public Set<String> ipHistory(UUID uuid) {
        Set<String> ips = new java.util.HashSet<>(ipHistory.getOrDefault(uuid, Set.of()));
        Optional.ofNullable(seen.get(uuid)).flatMap(SeenRecord::lastIp).ifPresent(ips::add);
        return Set.copyOf(ips);
    }

    @Override
    public List<UUID> altsByIp(String ip, UUID self) {
        return altsByAnyIp(Set.of(ip), self);
    }

    @Override
    public List<UUID> altsByAnyIp(Set<String> ips, UUID self) {
        if (ips.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<UUID> alts = new java.util.LinkedHashSet<>();
        seen.values().stream()
                .filter(record -> record.lastIp().filter(ips::contains).isPresent())
                .map(record -> record.player().uuid())
                .filter(uuid -> !uuid.equals(self))
                .forEach(alts::add);
        ipHistory.forEach((uuid, addresses) -> {
            if (!uuid.equals(self) && addresses.stream().anyMatch(ips::contains)) {
                alts.add(uuid);
            }
        });
        return List.copyOf(alts);
    }

    @Override
    public void ensureUserExists(PlayerRef target, Instant at) {
        ensured.add(target.uuid());
        seen.putIfAbsent(target.uuid(), SeenRecord.firstJoin(target, Optional.empty(), at));
    }

    @Override
    public boolean isLockedDown() {
        return lockedDown;
    }

    @Override
    public void setLockedDown(boolean enabled) {
        this.lockedDown = enabled;
    }

    private static <V> void upsert(Map<UUID, V> map, UUID key, V value, boolean delete) {
        if (delete) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }
}
