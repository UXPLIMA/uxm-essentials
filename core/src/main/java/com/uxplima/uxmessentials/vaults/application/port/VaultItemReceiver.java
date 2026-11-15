package com.uxplima.uxmessentials.vaults.application.port;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;

/** Puts vault items into a player's live inventory. Runs on the player's own thread. */
@FunctionalInterface
public interface VaultItemReceiver {

    /**
     * Add {@code items} to {@code owner}'s inventory. Empty when the owner is not online, and then nothing was given.
     * Otherwise reports how many items went in and the ones that did not fit, which are left for the caller to keep.
     */
    Optional<Receipt> give(PlayerRef owner, VaultContents items);

    /**
     * What one give did.
     *
     * @param given how many items went into the inventory
     * @param leftover the items that did not fit, empty when all did
     * @param leftoverCount how many items {@code leftover} holds
     */
    record Receipt(int given, VaultContents leftover, int leftoverCount) {

        public Receipt {
            Objects.requireNonNull(leftover, "leftover");
        }
    }
}
