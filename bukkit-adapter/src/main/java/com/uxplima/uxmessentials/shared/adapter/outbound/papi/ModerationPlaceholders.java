package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code muted} and {@code jailed} placeholders. It is an adapter
 * over the moderation context's {@code MutePolicy} and {@code JailGate} read sides, wired during
 * bootstrap; when the moderation module is disabled the seam is absent and both placeholders degrade to
 * "no".
 */
public interface ModerationPlaceholders {

    /** True when {@code who} is currently muted. */
    boolean isMuted(PlayerRef who);

    /** True when {@code who} is currently jailed. */
    boolean isJailed(PlayerRef who);
}
