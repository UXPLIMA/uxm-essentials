package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code homes_*} placeholders. It is an adapter over the homes
 * context's existing read ports ({@code HomeRepository.count} and the {@code HomeQuota} reducer) wired
 * during bootstrap; when the homes module is disabled the seam is absent and the placeholders degrade.
 *
 * <p>The count and limit are read for a specific player. The limit resolves through the same world-scoped
 * quota reducer {@code /sethome} uses, but the placeholder has no world in hand, so it resolves the
 * unscoped family (a {@code null} world) — the value an operator sees on a hub or a non-world-scoped
 * server. A negative {@code limit()} encodes "unlimited".
 */
public interface HomesPlaceholders {

    /** How many homes {@code who} currently holds. */
    int count(PlayerRef who);

    /** {@code who}'s resolved home limit, or a negative value when unlimited. */
    int limit(PlayerRef who);
}
