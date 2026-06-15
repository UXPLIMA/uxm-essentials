package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code muted}/{@code jailed} bare keys and the {@code moderation_*}
 * family. It is an adapter over the moderation context's read sides — the mute/jail gates, the
 * {@code ModerationRepository} ban/mute state, the {@code Sanctions} freeze read and the warning count — wired
 * during bootstrap; when the moderation module is disabled the seam is absent and the resolver degrades the
 * boolean placeholders to "no" and the rest to the dash.
 *
 * <p>Every "active" read is clock-gated exactly as the {@code /checkban}/{@code /checkmute} commands are, so a
 * placeholder agrees with what the player actually experiences: an expired tempban or timed mute reads as not
 * banned/muted even before the sweep removes its row.
 */
public interface ModerationPlaceholders {

    /** True when {@code who} is currently muted. */
    boolean isMuted(PlayerRef who);

    /** True when {@code who} is currently jailed. */
    boolean isJailed(PlayerRef who);

    /** True when {@code who} is currently frozen ({@code /freeze}). */
    boolean isFrozen(PlayerRef who);

    /** The count of {@code who}'s warnings still in effect now ({@code /warns}). */
    int warnCount(PlayerRef who);

    /** {@code who}'s active tempban/ban state, or empty when not banned. */
    Optional<SanctionView> activeBan(PlayerRef who);

    /** {@code who}'s active mute state, or empty when not muted. */
    Optional<SanctionView> activeMute(PlayerRef who);

    /**
     * A flattened view of an active sanction — the remaining wait, the reason and the issuer — so the resolver
     * renders the placeholders without importing a moderation domain type. {@code remaining} is empty for a
     * permanent sanction (a {@code /ban} or a permanent {@code /mute}), which the resolver renders as
     * {@code permanent}.
     *
     * @param remaining the time left until the sanction lifts, or empty for a permanent one
     * @param reason the issued reason, or the dash when none was given
     * @param issuer the name of the staff member (or console) who issued it
     */
    record SanctionView(Optional<Duration> remaining, String reason, String issuer) {}
}
