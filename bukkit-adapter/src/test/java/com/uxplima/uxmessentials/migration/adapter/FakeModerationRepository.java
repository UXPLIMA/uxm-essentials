package com.uxplima.uxmessentials.migration.adapter;

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
 * An in-memory {@link ModerationRepository} for the importer writer tests: the sanction tables the writer
 * touches (tempban by uuid, IP ban by address, the append-only warning history, the materialised seen set)
 * are real maps so a test can assert what landed; every method outside the import path throws, so a writer
 * change that starts touching an unexpected sanction surface fails loudly rather than silently passing.
 */
final class FakeModerationRepository implements ModerationRepository {

    final Map<UUID, TempbanState> tempbans = new ConcurrentHashMap<>();
    final Map<String, IpBan> ipBans = new ConcurrentHashMap<>();
    final Map<UUID, List<Warn>> warns = new ConcurrentHashMap<>();
    final Set<UUID> ensured = ConcurrentHashMap.newKeySet();

    @Override
    public ModerationProfile load(PlayerRef target) {
        TempbanState tempban = tempbans.getOrDefault(target.uuid(), TempbanState.none());
        return new ModerationProfile(target, MuteState.none(), JailState.none(), tempban);
    }

    @Override
    public TempbanState loadTempban(PlayerRef target) {
        return tempbans.getOrDefault(target.uuid(), TempbanState.none());
    }

    @Override
    public void saveTempban(PlayerRef target, TempbanState tempban) {
        if (tempban instanceof TempbanState.None) {
            tempbans.remove(target.uuid());
        } else {
            tempbans.put(target.uuid(), tempban);
        }
    }

    @Override
    public void saveIpBan(IpBan ban) {
        ipBans.put(ban.ip(), ban);
    }

    @Override
    public Optional<IpBan> activeIpBan(String ip, Instant now) {
        return Optional.ofNullable(ipBans.get(ip)).filter(ban -> ban.isActiveAt(now));
    }

    @Override
    public int appendWarn(PlayerRef target, Warn warn) {
        List<Warn> history = warns.computeIfAbsent(target.uuid(), uuid -> new ArrayList<>());
        history.add(warn);
        return history.size();
    }

    @Override
    public List<Warn> warns(PlayerRef target, Instant now) {
        List<Warn> history = warns.getOrDefault(target.uuid(), List.of());
        List<Warn> live = new ArrayList<>();
        for (Warn warn : history) {
            if (!warn.isExpiredAt(now)) {
                live.add(warn);
            }
        }
        return List.copyOf(live);
    }

    @Override
    public void ensureUserExists(PlayerRef target, Instant at) {
        ensured.add(target.uuid());
    }

    @Override
    public MuteState loadMute(PlayerRef target) {
        throw new UnsupportedOperationException("loadMute");
    }

    @Override
    public JailState loadJail(PlayerRef target) {
        throw new UnsupportedOperationException("loadJail");
    }

    @Override
    public List<BanEntry> activeBans(Instant now, int limit) {
        throw new UnsupportedOperationException("activeBans");
    }

    @Override
    public List<MuteEntry> activeMutes(Instant now, int limit) {
        throw new UnsupportedOperationException("activeMutes");
    }

    @Override
    public List<JailEntry> activeJails(Instant now, int limit) {
        throw new UnsupportedOperationException("activeJails");
    }

    @Override
    public void saveMute(PlayerRef target, MuteState mute) {
        throw new UnsupportedOperationException("saveMute");
    }

    @Override
    public void saveJail(PlayerRef target, JailState jail) {
        throw new UnsupportedOperationException("saveJail");
    }

    @Override
    public int clearWarns(PlayerRef target) {
        throw new UnsupportedOperationException("clearWarns");
    }

    @Override
    public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
        throw new UnsupportedOperationException("clearWarnsByActor");
    }

    @Override
    public boolean removeIpBan(String ip) {
        throw new UnsupportedOperationException("removeIpBan");
    }

    @Override
    public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
        throw new UnsupportedOperationException("recordSeen");
    }

    @Override
    public Optional<SeenRecord> seen(PlayerRef who) {
        throw new UnsupportedOperationException("seen");
    }

    @Override
    public boolean isLockedDown() {
        throw new UnsupportedOperationException("isLockedDown");
    }

    @Override
    public void setLockedDown(boolean enabled) {
        throw new UnsupportedOperationException("setLockedDown");
    }
}
