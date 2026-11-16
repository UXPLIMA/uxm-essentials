package com.uxplima.uxmessentials.vaults.application;

import java.util.Objects;

import com.uxplima.uxmessentials.vaults.domain.Vault;

/**
 * A vault ready to be shown, and the open fee taken for showing it, which the caller spends once the window is in
 * front of the player or gives back when it never gets there.
 *
 * @param vault the vault to show
 * @param fee the open fee this open took
 */
public record OpenedVault(Vault vault, VaultOpenFee fee) {

    public OpenedVault {
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(fee, "fee");
    }
}
