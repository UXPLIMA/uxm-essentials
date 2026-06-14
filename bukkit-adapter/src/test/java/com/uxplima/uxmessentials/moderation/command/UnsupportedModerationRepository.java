package com.uxplima.uxmessentials.moderation.command;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
 * A {@link ModerationRepository} whose every method throws, so a focused test overrides only the reads it
 * exercises and a method it never expects to be called fails loudly rather than returning a silent default.
 * Used by the check-command tests, which touch only {@code loadTempban}/{@code loadMute}.
 */
abstract class UnsupportedModerationRepository implements ModerationRepository {

    @Override
    public ModerationProfile load(PlayerRef target) {
        throw unsupported();
    }

    @Override
    public MuteState loadMute(PlayerRef target) {
        throw unsupported();
    }

    @Override
    public JailState loadJail(PlayerRef target) {
        throw unsupported();
    }

    @Override
    public TempbanState loadTempban(PlayerRef target) {
        throw unsupported();
    }

    @Override
    public List<BanEntry> activeBans(Instant now, int limit) {
        throw unsupported();
    }

    @Override
    public List<MuteEntry> activeMutes(Instant now, int limit) {
        throw unsupported();
    }

    @Override
    public List<JailEntry> activeJails(Instant now, int limit) {
        throw unsupported();
    }

    @Override
    public void saveMute(PlayerRef target, MuteState mute) {
        throw unsupported();
    }

    @Override
    public void saveJail(PlayerRef target, JailState jail) {
        throw unsupported();
    }

    @Override
    public void saveTempban(PlayerRef target, TempbanState tempban) {
        throw unsupported();
    }

    @Override
    public int appendWarn(PlayerRef target, Warn warn) {
        throw unsupported();
    }

    @Override
    public List<Warn> warns(PlayerRef target, Instant now) {
        throw unsupported();
    }

    @Override
    public int clearWarns(PlayerRef target) {
        throw unsupported();
    }

    @Override
    public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
        throw unsupported();
    }

    @Override
    public void saveIpBan(IpBan ban) {
        throw unsupported();
    }

    @Override
    public boolean removeIpBan(String ip) {
        throw unsupported();
    }

    @Override
    public Optional<IpBan> activeIpBan(String ip, Instant now) {
        throw unsupported();
    }

    @Override
    public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
        throw unsupported();
    }

    @Override
    public void recordIpSeen(UUID uuid, String ip, Instant now) {
        throw unsupported();
    }

    @Override
    public Optional<SeenRecord> seen(PlayerRef who) {
        throw unsupported();
    }

    @Override
    public Set<String> ipHistory(UUID uuid) {
        throw unsupported();
    }

    @Override
    public List<UUID> altsByIp(String ip, UUID self) {
        throw unsupported();
    }

    @Override
    public List<UUID> altsByAnyIp(Set<String> ips, UUID self) {
        throw unsupported();
    }

    @Override
    public void ensureUserExists(PlayerRef target, Instant at) {
        throw unsupported();
    }

    @Override
    public boolean isLockedDown() {
        throw unsupported();
    }

    @Override
    public void setLockedDown(boolean enabled) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("the check commands read only loadTempban/loadMute");
    }
}
