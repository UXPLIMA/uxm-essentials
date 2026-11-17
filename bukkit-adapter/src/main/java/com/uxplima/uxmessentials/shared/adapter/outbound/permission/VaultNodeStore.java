package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import net.milkbowl.vault.permission.Permission;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link NodeStore} backed by the permission provider registered with Vault, for a server without LuckPerms. It
 * asks the provider about the offline player, which most providers answer from their own storage.
 *
 * <p>Vault has no "not set": a provider answers true or false, and many ignore a node's operator default for an
 * offline player. So a false here only means this provider grants nothing; the operator default is applied by the
 * caller on top. A player who never joined this server has no name here, and Vault asks providers by name, so they
 * cannot be looked up at all.
 *
 * <p>Vault registers its own hooks (PermissionsEx, GroupManager and others) under its own plugin, so a provider is
 * told apart by its name: only {@code SuperPerms}, Vault's stand-in when no permission plugin is installed, is left
 * out. It is reached only once a Vault permission registration has been found, so a server without Vault never loads
 * this class.
 */
@NullMarked
final class VaultNodeStore implements NodeStore {

    /** The name of Vault's stand-in provider, which reads only what an online player has. */
    static final String SUPER_PERMS = "SuperPerms";

    private final Server server;
    private final Permission provider;

    private VaultNodeStore(Server server, Permission provider) {
        this.server = Objects.requireNonNull(server, "server");
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /** The store over {@code registration}'s provider, or null when it is disabled or Vault's stand-in. */
    static @Nullable NodeStore of(Server server, RegisteredServiceProvider<?> registration) {
        if (!(registration.getProvider() instanceof Permission provider)
                || !provider.isEnabled()
                || SUPER_PERMS.equals(provider.getName())) {
            return null;
        }
        return new VaultNodeStore(server, provider);
    }

    @Override
    public Load prepare(UUID player, String node) {
        Objects.requireNonNull(node, "node");
        OfflinePlayer offline = server.getOfflinePlayer(Objects.requireNonNull(player, "player"));
        if (offline.getName() == null) {
            return () -> CompletableFuture.completedFuture(Answer.NO_SOURCE);
        }
        // The provider may read its database for a player who is not online, so the call is left to the async lane.
        return () -> CompletableFuture.completedFuture(
                provider.playerHas((String) null, offline, node) ? Answer.HELD : Answer.NOT_HELD);
    }
}
