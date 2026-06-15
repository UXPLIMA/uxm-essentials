package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code vaults_*} placeholders. It is an adapter over the vaults
 * context's {@code VaultRepository.count} and the two numbered quota families resolved through the shared
 * {@code Permissions} reducer ({@code uxmessentials.vault.amount.<n>} and {@code uxmessentials.vault.size.
 * <rows>}), wired during bootstrap; when the vaults module is disabled the seam is absent and the
 * placeholders degrade.
 *
 * <p>The amount quota resolves the same way {@code /vault <n>} enforces it, so the count/max/left scalars a
 * placeholder reports match what the command admits. A negative {@link #max(PlayerRef)} encodes "unlimited",
 * matching the homes-limit seam convention so the resolver renders it as the infinity marker.
 */
public interface VaultsPlaceholders {

    /** How many vaults {@code who} currently has rows for. */
    int count(PlayerRef who);

    /** {@code who}'s resolved vault-count quota ({@code vault.amount}), or a negative value when unlimited. */
    int max(PlayerRef who);

    /** {@code who}'s resolved per-vault size in rows ({@code vault.size}). */
    int size(PlayerRef who);
}
