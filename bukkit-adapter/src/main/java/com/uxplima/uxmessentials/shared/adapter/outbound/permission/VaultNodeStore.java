package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.permission.Permission;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NodeStore} backed by a permission plugin registered with Vault, for a server without LuckPerms. It asks
 * the provider about the offline player, which most providers answer from their own storage; the provider's answer is
 * taken as it is, since Vault reports no "not set". It is constructed only once a Vault permission registration has
 * been found, so a server without Vault never loads this class.
 */
@NullMarked
final class VaultNodeStore implements NodeStore {

    private final Server server;
    private final Permission provider;

    VaultNodeStore(Server server, RegisteredServiceProvider<?> registration) {
        this.server = Objects.requireNonNull(server, "server");
        this.provider = (Permission) registration.getProvider();
    }

    @Override
    public Load prepare(UUID player, String node) {
        Objects.requireNonNull(node, "node");
        OfflinePlayer offline = server.getOfflinePlayer(Objects.requireNonNull(player, "player"));
        // The provider may read its database for a player who is not online, so the call is left to the async lane.
        return () -> CompletableFuture.completedFuture(Optional.of(provider.playerHas(null, offline, node)));
    }
}
