package com.uxplima.uxmessentials.vaults.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorView;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.DeleteVault;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenAdminVault;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed collaborators the vaults inbound adapter (the {@code /vault} command and the close listener)
 * shares: the four use cases, the player-facing {@link VaultNotifier}, the GUI {@link VaultView}, and the
 * {@link KernelPorts} for the scheduler/player-lookup the command and listener reach for. Assembled once in
 * {@code VaultsWiring} so the command and listener stay focused on mapping a Bukkit event to one use-case call.
 *
 * @param openVault resolves and allocates a player's vault
 * @param listVaults the owned-vault listing
 * @param openAdminVault the audit-logged staff inspect override
 * @param deleteVault removes a vault row (own with refund, or the audited staff override)
 * @param saveVault the write-through on close
 * @param amountQuota the owner's vault-count cap, read by {@code /vault info}
 * @param sizeQuota the owner's per-vault row count, read by {@code /vault info}
 * @param notifier player-facing feedback
 * @param view the inventory-holder GUI builder
 * @param selector the paginated vault-picker the no-arg {@code /vault} opens when several vaults are owned
 * @param selectorEnabled whether {@code /vault} routes to the picker (else the chat list) for a multi-vault owner
 * @param chargeSettings the create/open fees and delete refund, so the cannot-afford notice can name the cost
 * @param kernel the shared outbound ports (scheduler, player lookup, events)
 */
@NullMarked
public record VaultServices(
        OpenVault openVault,
        ListVaults listVaults,
        OpenAdminVault openAdminVault,
        DeleteVault deleteVault,
        SaveVault saveVault,
        VaultAmountQuota amountQuota,
        VaultSizeQuota sizeQuota,
        VaultNotifier notifier,
        VaultView view,
        VaultSelectorView selector,
        boolean selectorEnabled,
        VaultChargeSettings chargeSettings,
        KernelPorts kernel) {

    public VaultServices {
        Objects.requireNonNull(openVault, "openVault");
        Objects.requireNonNull(listVaults, "listVaults");
        Objects.requireNonNull(openAdminVault, "openAdminVault");
        Objects.requireNonNull(deleteVault, "deleteVault");
        Objects.requireNonNull(saveVault, "saveVault");
        Objects.requireNonNull(amountQuota, "amountQuota");
        Objects.requireNonNull(sizeQuota, "sizeQuota");
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(chargeSettings, "chargeSettings");
        Objects.requireNonNull(kernel, "kernel");
    }
}
