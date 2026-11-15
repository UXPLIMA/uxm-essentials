package com.uxplima.uxmessentials.vaults.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemHandBack;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;

/**
 * Writes back a vault window that has closed (or is still open when the module stops). The owner's own window updates
 * the existing row only. When the vault was deleted while the window was open, it stays deleted and the items still in
 * the window go back to the owner through {@link VaultItemHandBack}, now or on their next join. Bringing the row back
 * would give the owner a vault they were already refunded for, or undo a staff delete.
 *
 * <p>A staff window keeps the upsert: the staff member is not the owner the items belong to, and it is how staff fill
 * a vault the owner has not opened yet. A vault brought back that way records no payment, so deleting it again
 * refunds nothing.
 */
public final class CloseVaultWindow {

    private final SaveVault saveVault;
    private final VaultItemHandBack handBack;

    public CloseVaultWindow(SaveVault saveVault, VaultItemHandBack handBack) {
        this.saveVault = Objects.requireNonNull(saveVault, "saveVault");
        this.handBack = Objects.requireNonNull(handBack, "handBack");
    }

    /** Write back {@code contents}, the items left in {@code viewer}'s window on {@code owner}'s {@code vault}. */
    public void close(PlayerRef viewer, PlayerRef owner, Vault vault, VaultContents contents) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(contents, "contents");
        if (!viewer.uuid().equals(owner.uuid())) {
            saveVault.keep(owner, vault, contents);
            return;
        }
        if (!saveVault.save(owner, vault, contents) && !contents.isEmpty()) {
            handBack.handBack(owner, contents);
        }
    }
}
