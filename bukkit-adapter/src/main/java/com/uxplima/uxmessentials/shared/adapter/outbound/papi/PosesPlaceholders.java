package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code poses_*} placeholders. It is an adapter over the poses context's
 * {@code PoseSessions} registry, wired during poses wiring; when the poses module is disabled the seam is absent
 * and the placeholder degrades to the dash.
 *
 * <p>The only Phase-1 read is whether the requesting player is currently sitting. A pose is live session state, so
 * it holds no value for an offline requester; the resolver's offline guard degrades the placeholder to the dash
 * for them.
 */
public interface PosesPlaceholders {

    /** Whether {@code who} is currently sitting. */
    boolean sitting(PlayerRef who);
}
