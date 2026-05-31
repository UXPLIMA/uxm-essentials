package com.uxplima.uxmessentials.vaults.domain;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Identity of a single vault: its owner and the index the player addresses it by ({@code /vault <n>}). The
 * pair is the aggregate's natural key and mirrors the {@code (owner, idx)} primary key of the {@code vaults}
 * table, so a vault is resolved from a GUI holder back to its row without inventing a surrogate id.
 *
 * <p>The index is one-based as a player types it; {@code 1} is the default vault {@code /vault} opens. A
 * non-positive index is rejected at construction so an out-of-range request never reaches the repository.
 *
 * @param owner the player who owns the vault
 * @param index the one-based vault number the player addresses
 */
public record VaultId(UUID owner, int index) {

    public VaultId {
        Objects.requireNonNull(owner, "owner");
        if (index < 1) {
            throw new IllegalArgumentException("vault index must be at least 1: " + index);
        }
    }

    /** The id of {@code owner}'s vault at {@code index}. */
    public static VaultId of(PlayerRef owner, int index) {
        return new VaultId(Objects.requireNonNull(owner, "owner").uuid(), index);
    }
}
