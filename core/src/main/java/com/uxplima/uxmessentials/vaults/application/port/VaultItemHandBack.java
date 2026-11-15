package com.uxplima.uxmessentials.vaults.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;

/**
 * Gives the items of a vault window back to the vault's owner when the vault they belong to is gone: into the owner's
 * live inventory as far as it has room, and the rest, or all of it while the owner is away, into
 * {@link VaultItemReturns} for their next join. It never writes them back into a vault row.
 */
@FunctionalInterface
public interface VaultItemHandBack {

    /** Give {@code items} to {@code owner}, now or on their next join. */
    void handBack(PlayerRef owner, VaultContents items);
}
