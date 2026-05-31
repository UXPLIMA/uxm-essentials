package com.uxplima.uxmessentials.vaults.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenAdminVault;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed collaborators the vaults inbound adapter (the {@code /vault} command and the close listener)
 * shares: the four use cases, the player-facing {@link VaultNotifier}, the GUI {@link VaultView}, and the
 * {@link KernelPorts} for the scheduler/player-lookup the command and listener reach for. Assembled once in
 * {@code VaultsWiring} so the command and listener stay focused on mapping a Bukkit event to one use-case call.
 *
 * @param openVault resolves and allocates a player's vault
 * @param listVaults the owned-vault listing
 * @param openAdminVault the audit-logged staff override
 * @param saveVault the write-through on close
 * @param notifier player-facing feedback
 * @param view the inventory-holder GUI builder
 * @param kernel the shared outbound ports (scheduler, player lookup, events)
 */
@NullMarked
public record VaultServices(
        OpenVault openVault,
        ListVaults listVaults,
        OpenAdminVault openAdminVault,
        SaveVault saveVault,
        VaultNotifier notifier,
        VaultView view,
        KernelPorts kernel) {

    public VaultServices {
        Objects.requireNonNull(openVault, "openVault");
        Objects.requireNonNull(listVaults, "listVaults");
        Objects.requireNonNull(openAdminVault, "openAdminVault");
        Objects.requireNonNull(saveVault, "saveVault");
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(kernel, "kernel");
    }
}
