package com.uxplima.uxmessentials.moderation.adapter;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * The ban and mute rows a read side asks for, keyed by player, and a switch that takes the database down so every read
 * throws. Nothing else of the store is touched.
 */
public final class SanctionRows implements ModerationRepository {

    private final Map<UUID, TempbanState> tempbans = new HashMap<>();
    private final Map<UUID, MuteState> mutes = new HashMap<>();
    private boolean down;

    public void ban(PlayerRef target, TempbanState ban) {
        tempbans.put(target.uuid(), ban);
    }

    public void mute(PlayerRef target, MuteState mute) {
        mutes.put(target.uuid(), mute);
    }

    /** From now on every read fails, as a database that went away does. */
    public void goDown() {
        down = true;
    }

    private void answer() {
        if (down) {
            throw new IllegalStateException("the database is unreachable");
        }
    }

    @Override
    public TempbanState loadTempban(PlayerRef target) {
        answer();
        return tempbans.getOrDefault(target.uuid(), TempbanState.none());
    }

    @Override
    public ModerationProfile load(PlayerRef target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MuteState loadMute(PlayerRef target) {
        answer();
        return mutes.getOrDefault(target.uuid(), MuteState.none());
    }

    @Override
    public JailState loadJail(PlayerRef target) {
        throw new UnsupportedOperationException();
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
    public void saveJail(PlayerRef target, JailState jail) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveTempban(PlayerRef target, TempbanState tempban) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replaceMute(PlayerRef target, MuteState expected, MuteState replacement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<IpBan> loadIpBan(String ip) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replaceIpBan(Optional<IpBan> expected, IpBan replacement) {
        throw new UnsupportedOperationException();
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
    public void saveIpBan(IpBan ban) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIpBan(String ip) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<IpBan> activeIpBan(String ip, Instant now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<SeenRecord> seen(PlayerRef who) {
        // No seen rows are kept here: every account reads as never seen.
        return Optional.empty();
    }

    @Override
    public void ensureUserExists(PlayerRef target, Instant at) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLockedDown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLockedDown(boolean enabled) {
        throw new UnsupportedOperationException();
    }
}
