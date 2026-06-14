package com.uxplima.uxmessentials.vaults.command;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorView;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorView.VaultSelectorSettings;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.domain.VaultItemPolicy;

/**
 * Shared test fixtures for the vaults command/GUI tests: a {@link VaultView} and {@link VaultSelectorView}
 * wired off a {@link KernelPorts} double the same way {@code VaultsWiring} wires them, so each test's
 * {@code services()} helper stays a single call rather than repeating the selector plumbing. The selector
 * settings default to a three-row picker showing locked indices, mirroring the shipped config.
 */
final class VaultViews {

    private VaultViews() {}

    /** A {@link VaultView} with the given blacklist policy, wired off the kernel doubles. */
    static VaultView view(KernelPorts kernel, SaveVault saveVault, VaultItemPolicy policy) {
        return new VaultView(
                kernel.messages(), kernel.messageSink(), saveVault, kernel.scheduler(), kernel.permissions(), policy);
    }

    /** A {@link VaultView} that blocks nothing — the default for tests not exercising the blacklist. */
    static VaultView view(KernelPorts kernel, SaveVault saveVault) {
        return view(kernel, saveVault, VaultItemPolicy.allowAll());
    }

    /** The default three-row picker settings: a CHEST owned icon, a grey-pane locked icon, locked shown. */
    static VaultSelectorSettings selectorSettings() {
        GuiLayout layout = new GuiLayout(3, Material.CHEST, Material.ARROW, 21, 23, java.util.List.of());
        return new VaultSelectorSettings(layout, Material.CHEST, Material.GRAY_STAINED_GLASS_PANE, true);
    }

    /** A {@link VaultSelectorView} wired off the kernel doubles and the given collaborators. */
    static VaultSelectorView selector(
            KernelPorts kernel,
            ListVaults listVaults,
            VaultAmountQuota amountQuota,
            OpenVault openVault,
            VaultView view,
            VaultNotifier notifier,
            VaultChargeSettings chargeSettings) {
        return new VaultSelectorView(
                kernel.messages(),
                kernel.messageSink(),
                kernel.scheduler(),
                listVaults,
                amountQuota,
                openVault,
                view,
                notifier,
                chargeSettings,
                selectorSettings());
    }
}
