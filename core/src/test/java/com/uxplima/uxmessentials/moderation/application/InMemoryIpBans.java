package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * A moderation store that keeps IP bans in memory, with no lockdown and no player ban: what the IP ban use cases
 * and the login check read, plus the seen records {@code /seen} reads.
 */
final class InMemoryIpBans implements ModerationRepository {

    private final Map<String, IpBan> ipBans = new HashMap<>();
    private final Map<java.util.UUID, SeenRecord> seen = new HashMap<>();
    private final Map<java.util.UUID, TempbanState> tempbans = new HashMap<>();

    /** Keep {@code record} as the seen record of its player. */
    void recordSeen(SeenRecord record) {
        seen.put(record.player().uuid(), record);
    }

    @Override
    public void saveIpBan(IpBan ban) {
        ipBans.put(ban.ip(), ban);
    }

    @Override
    public Optional<IpBan> loadIpBan(String ip) {
        return Optional.ofNullable(ipBans.get(ip));
    }

    @Override
    public boolean replaceIpBan(Optional<IpBan> expected, IpBan replacement) {
        if (!loadIpBan(replacement.ip()).equals(expected)) {
            return false;
        }
        ipBans.put(replacement.ip(), replacement);
        return true;
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
    public ModerationProfile load(PlayerRef target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MuteState loadMute(PlayerRef target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JailState loadJail(PlayerRef target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TempbanState loadTempban(PlayerRef target) {
        return tempbans.getOrDefault(target.uuid(), TempbanState.none());
    }

    @Override
    public List<BanEntry> activeBans(Instant now, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<MuteEntry> activeMutes(Instant now, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<JailEntry> activeJails(Instant now, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveMute(PlayerRef target, MuteState mute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replaceMute(PlayerRef target, MuteState expected, MuteState replacement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveJail(PlayerRef target, JailState jail) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveTempban(PlayerRef target, TempbanState tempban) {
        tempbans.put(target.uuid(), tempban);
    }

    @Override
    public boolean replaceTempban(PlayerRef target, TempbanState expected, TempbanState.Active replacement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int appendWarn(PlayerRef target, Warn warn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Warn> warns(PlayerRef target, Instant now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int clearWarns(PlayerRef target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
        // Last-seen records are not what these tests look at.
    }

    @Override
    public Optional<SeenRecord> seen(PlayerRef who) {
        return Optional.ofNullable(seen.get(who.uuid()));
    }

    @Override
    public void ensureUserExists(PlayerRef target, Instant at) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLockedDown() {
        return false;
    }

    @Override
    public void setLockedDown(boolean enabled) {
        throw new UnsupportedOperationException();
    }
}
