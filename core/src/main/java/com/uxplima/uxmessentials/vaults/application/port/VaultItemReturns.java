package com.uxplima.uxmessentials.vaults.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.vaults.domain.VaultContents;

/**
 * Durable keeping for the items of a vault window whose vault was deleted while it was open, held while the owner is
 * not online to take them, and handed over on their next join. The items stay in the vault's own serialized form, so
 * an enchantment, a name or a shulker's contents survive the wait. A port because it is a storage boundary; every
 * call does I/O and runs off the tick.
 */
public interface VaultItemReturns {

    /** Keep {@code items} for {@code owner}, on top of anything already kept for them. */
    void keep(UUID owner, VaultContents items);

    /**
     * Take everything kept for {@code owner} and remove it. Each kept batch goes to exactly one caller, even when two
     * takes run at once on servers sharing the database. Empty when nothing is kept.
     */
    List<VaultContents> takeAll(UUID owner);
}
