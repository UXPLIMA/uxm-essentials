package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

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
import org.jspecify.annotations.Nullable;

/**
 * The warn, mute, tempban and IP ban rows of the moderation store, plus the lockdown switch; no player is jailed. Like the real store, the
 * count a new warning returns leaves out warnings that had lapsed when it was issued.
 */
final class SanctionRecords implements ModerationRepository {

    private final Map<UUID, List<Warn>> warns = new HashMap<>();
    private final Map<UUID, MuteState> mutes = new HashMap<>();
    private final Map<UUID, TempbanState> tempbans = new HashMap<>();
    private final Map<String, IpBan> ipBans = new HashMap<>();
    private final List<UUID> seen = new ArrayList<>();
    private boolean lockedDown;
    private boolean unreachable;
    private @Nullable Runnable racingWrite;
    private boolean losingEveryRace;
    private int failingMuteReads;
    private @Nullable Runnable duringMuteRead;

    /** Every warning row of {@code target}, lapsed ones included. */
    List<Warn> allWarns(PlayerRef target) {
        return List.copyOf(warns.getOrDefault(target.uuid(), List.of()));
    }

    /** Let the next {@code times} reads of a single mute fail, as a database that is briefly unreachable does. */
    void failMuteReads(int times) {
        failingMuteReads = times;
    }

    /** Run {@code meanwhile} once, inside the next read of a single mute, after the row was read. */
    void duringNextMuteRead(Runnable meanwhile) {
        duringMuteRead = meanwhile;
    }

    /** Make every read a login check makes fail from now on, as a database that went away does. */
    void goUnreachable() {
        unreachable = true;
    }

    private void reach() {
        if (unreachable) {
            throw new IllegalStateException("the database is unreachable");
        }
    }

    /** Whose logins were recorded, in order. */
    List<UUID> seen() {
        return List.copyOf(seen);
    }

    @Override
    public int appendWarn(PlayerRef target, Warn warn) {
        warns.computeIfAbsent(target.uuid(), key -> new ArrayList<>()).add(warn);
        return warns(target, warn.issuedAt()).size();
    }

    @Override
    public List<Warn> warns(PlayerRef target, Instant now) {
        return warns.getOrDefault(target.uuid(), List.of()).stream()
                .filter(warn -> !warn.isExpiredAt(now))
                .toList();
    }

    @Override
    public int clearWarns(PlayerRef target) {
        List<Warn> removed = warns.remove(target.uuid());
        return removed == null ? 0 : removed.size();
    }

    @Override
    public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
        List<Warn> rows = warns.getOrDefault(target.uuid(), new ArrayList<>());
        int before = rows.size();
        rows.removeIf(warn -> warn.issuer().uuid().equals(Optional.of(actor.uuid())));
        return before - rows.size();
    }

    @Override
    public MuteState loadMute(PlayerRef target) {
        if (failingMuteReads > 0) {
            failingMuteReads--;
            throw new IllegalStateException("the database is unreachable");
        }
        MuteState read = mutes.getOrDefault(target.uuid(), MuteState.none());
        Runnable meanwhile = duringMuteRead;
        duringMuteRead = null;
        if (meanwhile != null) {
            meanwhile.run();
        }
        return read;
    }

    @Override
    public void saveMute(PlayerRef target, MuteState mute) {
        mutes.put(target.uuid(), mute);
    }

    @Override
    public TempbanState loadTempban(PlayerRef target) {
        reach();
        return tempbans.getOrDefault(target.uuid(), TempbanState.none());
    }

    @Override
    public void saveTempban(PlayerRef target, TempbanState tempban) {
        tempbans.put(target.uuid(), tempban);
    }

    /**
     * Run {@code write} once, just before the next conditional ban, mute or IP ban write is checked: another server's
     * sanction landing between a use case's read and its write.
     */
    void beforeNextReplace(Runnable write) {
        racingWrite = write;
    }

    /** Make every conditional write lose its race and write nothing, as if the row changed each time. */
    void loseEveryReplace() {
        losingEveryRace = true;
    }

    /** Whether the conditional write may land on a row that is {@code current}, after any racing write has run. */
    private boolean landsOn(Supplier<Object> current, Object expected) {
        Runnable racing = racingWrite;
        racingWrite = null;
        if (racing != null) {
            racing.run();
        }
        return !losingEveryRace && current.get().equals(expected);
    }

    @Override
    public boolean replaceTempban(PlayerRef target, TempbanState expected, TempbanState.Active replacement) {
        if (!landsOn(() -> loadTempban(target), expected)) {
            return false;
        }
        tempbans.put(target.uuid(), replacement);
        return true;
    }

    @Override
    public boolean replaceMute(PlayerRef target, MuteState expected, MuteState replacement) {
        if (!landsOn(() -> loadMute(target), expected)) {
            return false;
        }
        mutes.put(target.uuid(), replacement);
        return true;
    }

    @Override
    public Optional<IpBan> loadIpBan(String ip) {
        return Optional.ofNullable(ipBans.get(ip));
    }

    @Override
    public boolean replaceIpBan(Optional<IpBan> expected, IpBan replacement) {
        if (!landsOn(() -> loadIpBan(replacement.ip()), expected)) {
            return false;
        }
        ipBans.put(replacement.ip(), replacement);
        return true;
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
        reach();
        return Optional.ofNullable(ipBans.get(ip)).filter(ban -> ban.isActiveAt(now));
    }

    @Override
    public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
        seen.add(who.uuid());
    }

    @Override
    public Optional<SeenRecord> seen(PlayerRef who) {
        return Optional.empty();
    }

    @Override
    public void ensureUserExists(PlayerRef target, Instant at) {
        // The users table is not what these tests look at.
    }

    @Override
    public boolean isLockedDown() {
        reach();
        return lockedDown;
    }

    @Override
    public void setLockedDown(boolean enabled) {
        lockedDown = enabled;
    }

    @Override
    public ModerationProfile load(PlayerRef target) {
        return new ModerationProfile(target, loadMute(target), JailState.none(), loadTempban(target));
    }

    @Override
    public JailState loadJail(PlayerRef target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveJail(PlayerRef target, JailState jail) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BanEntry> activeBans(Instant now, int limit) {
        List<BanEntry> active = new ArrayList<>();
        tempbans.forEach((target, state) -> {
            if (state instanceof TempbanState.Active ban && ban.isActiveAt(now)) {
                active.add(new BanEntry(target, ban.issuer(), ban.reason(), ban.until(), ban.issuedAt()));
            }
        });
        return active.stream().limit(limit).toList();
    }

    @Override
    public List<MuteEntry> activeMutes(Instant now, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<JailEntry> activeJails(Instant now, int limit) {
        throw new UnsupportedOperationException();
    }
}
