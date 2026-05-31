package com.uxplima.uxmessentials.moderation.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The DB-backed sanction store (the hard moderation invariant: state survives restart, never PDC). Each
 * sanction kind is its own table behind this one port; the {@link ModerationProfile} read rebuilds a
 * target's mute, jail and tempban in one shot, while the writes are keyed per kind so an {@code /unmute}
 * touches only the mute row.
 *
 * <p>An offline target's row is lazily materialized before any FK-bearing write ({@code ensureUserExists},
 * docs/09-deployment) so an offline {@code /jail} or {@code /banip} never breaks referential integrity. The
 * sweep methods remove sanctions whose wall-clock expiry has passed; the online-only jail countdown is not
 * swept here — it is decremented by the join/quit tick and released by {@code /unjail}.
 */
public interface ModerationRepository {

    /** Rebuild {@code target}'s mute/jail/tempban from the per-sanction tables; a clean target yields no rows. */
    ModerationProfile load(PlayerRef target);

    /** The current mute alone, for the {@code MutePolicy} hot path (no jail/tempban read). */
    MuteState loadMute(PlayerRef target);

    /** The current jail alone, for the {@code JailGate} hot path and the online-only tick. */
    JailState loadJail(PlayerRef target);

    /** The current tempban alone, for the ban-on-login check. */
    TempbanState loadTempban(PlayerRef target);

    /** Upsert {@code target}'s mute. {@link MuteState.None} deletes the row. */
    void saveMute(PlayerRef target, MuteState mute);

    /** Upsert {@code target}'s jail. {@link JailState.None} deletes the row. */
    void saveJail(PlayerRef target, JailState jail);

    /** Upsert {@code target}'s tempban. {@link TempbanState.None} deletes the row. */
    void saveTempban(PlayerRef target, TempbanState tempban);

    /** Append one warning to {@code target}'s history and return the new total count. */
    int appendWarn(PlayerRef target, Warn warn);

    /** {@code target}'s warning history, newest-first. */
    List<Warn> warns(PlayerRef target);

    /** Upsert an IP ban (keyed by address); a re-ban of the same address overwrites. */
    void saveIpBan(IpBan ban);

    /** Remove the IP ban for {@code ip}; returns true when a row was removed. */
    boolean removeIpBan(String ip);

    /** The active IP ban for {@code ip} at {@code now}, if any (expired bans are not returned). */
    Optional<IpBan> activeIpBan(String ip, Instant now);

    /** Record or advance {@code who}'s last-seen/last-IP on join/quit. */
    void recordSeen(PlayerRef who, Optional<String> ip, Instant at);

    /** {@code who}'s last-seen/last-IP record, if ever recorded. */
    Optional<SeenRecord> seen(PlayerRef who);

    /** Other UUIDs that have connected from {@code ip} (the alt set), excluding {@code self}. */
    List<UUID> altsByIp(String ip, UUID self);

    /** Lazily materialize {@code target}'s seen row so an offline FK-bearing write never breaks integrity. */
    void ensureUserExists(PlayerRef target, Instant at);
}
