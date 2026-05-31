package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code vaults_count} placeholder. It is an adapter over the
 * vaults context's {@code VaultRepository.count}, wired during bootstrap; when the vaults module is
 * disabled the seam is absent and the placeholder degrades.
 */
public interface VaultsPlaceholders {

    /** How many vaults {@code who} currently has rows for. */
    int count(PlayerRef who);
}
