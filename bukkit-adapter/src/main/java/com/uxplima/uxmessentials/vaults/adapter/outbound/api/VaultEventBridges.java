package com.uxplima.uxmessentials.vaults.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.vault.UxmVaultContentsChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.vault.UxmVaultOpenEvent;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.vaults.domain.event.VaultContentsChanged;
import com.uxplima.uxmessentials.vaults.domain.event.VaultOpened;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each vault fact becomes.
 *
 * <p>An open follows the viewer, who is the one with an inventory on screen; a contents change follows the owner,
 * whose stored items actually changed.
 */
@NullMarked
public final class VaultEventBridges {

    private VaultEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                VaultOpened.class,
                UxmVaultOpenEvent.getHandlerList(),
                fact -> new UxmVaultOpenEvent(
                        fact.owner().uuid(),
                        fact.owner().name(),
                        fact.viewer().uuid(),
                        fact.viewer().name(),
                        fact.index(),
                        fact.at()),
                fact -> Region.entity(fact.viewer()));
        registry.register(
                VaultContentsChanged.class,
                UxmVaultContentsChangeEvent.getHandlerList(),
                fact -> new UxmVaultContentsChangeEvent(
                        fact.owner().uuid(), fact.owner().name(), fact.index(), fact.at()),
                fact -> Region.entity(fact.owner()));
    }
}
