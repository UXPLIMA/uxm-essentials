package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code security_*} placeholders. It reports only the state the module
 * already holds in memory: whether a player is mid-challenge and whether they are held in the freeze that goes
 * with it. What factors an account has enrolled is deliberately not here, because that answer lives in the
 * database and a HUD refresh must never become a query.
 *
 * <p>Wired during security wiring; with the module disabled the seam is absent and both keys degrade to the dash.
 */
public interface SecurityPlaceholders {

    /** Whether {@code who} has an open verification challenge and has not answered it yet. */
    boolean verifying(PlayerRef who);

    /** Whether the module requires a factor from somebody before it lets them play at all. */
    boolean enforced();
}
