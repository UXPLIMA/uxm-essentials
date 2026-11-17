package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reads a node for a player who is not online here, the way the server they would play on applies it.
 *
 * <p>With LuckPerms, the user is loaded from its storage on the async lane, never on the tick, and a node it sets
 * wins. A node it leaves unset falls back to the node's declared default against the player's stored operator status,
 * as it would for a player online here.
 *
 * <p>Without LuckPerms:
 *
 * <ul>
 *   <li>a player online on another backend is asked about over the bus, and that backend reads the node live; with no
 *       bus, or no answer, there is {@link Answer#NO_SOURCE}, never a guess;
 *   <li>an offline player is asked about through the permission plugin registered with Vault, on the async lane;
 *   <li>with no permission plugin at all, the server's own grants answer: the declared default against the operator
 *       list.
 * </ul>
 *
 * <p>The default, the operator status and the provider are read on the calling server thread before anything leaves
 * it.
 */
@NullMarked
public final class ServerStoredPermissions implements StoredPermissions {

    private static final String VAULT_PERMISSION = "net.milkbowl.vault.permission.Permission";
    private static final String VAULT = "Vault";

    private final Server server;
    private final Scheduler scheduler;
    private final NetworkPresence presence;
    private final Logger log;

    @Nullable private final NodeStore luckPerms;

    private final Supplier<@Nullable NodeStore> vault;
    private final Optional<BusPermissionReads> bus;

    ServerStoredPermissions(
            Server server,
            Scheduler scheduler,
            NetworkPresence presence,
            Logger log,
            @Nullable NodeStore luckPerms,
            Supplier<@Nullable NodeStore> vault,
            Optional<BusPermissionReads> bus) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.log = Objects.requireNonNull(log, "log");
        this.luckPerms = luckPerms;
        this.vault = Objects.requireNonNull(vault, "vault");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    /**
     * The reader for this server: the LuckPerms storage when LuckPerms is enabled, otherwise the backend a player is on
     * through {@code bus} (empty off the network), the Vault permission provider, or the server's own grants.
     */
    public static ServerStoredPermissions of(
            Server server,
            Scheduler scheduler,
            NetworkPresence presence,
            Logger log,
            Optional<BusPermissionReads> bus) {
        // Constructed by a direct new past the guard, so a server without LuckPerms never loads its classes.
        NodeStore luckPerms = LuckPermsAccess.isPresent(server) ? new LuckPermsNodeStore() : null;
        return new ServerStoredPermissions(
                server, scheduler, presence, log, luckPerms, () -> vaultProvider(server), bus);
    }

    @Override
    public CompletableFuture<Answer> read(PlayerRef who, String node) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(node, "node");
        boolean byDefault = byDefault(who.uuid(), node);
        if (luckPerms != null) {
            return load(luckPerms, who, node, byDefault);
        }
        Optional<String> elsewhere = anotherBackend(who.uuid());
        if (elsewhere.isPresent()) {
            return bus.map(reads -> reads.ask(who, node, elsewhere.get()))
                    .orElseGet(() -> CompletableFuture.completedFuture(Answer.NO_SOURCE));
        }
        NodeStore provider = vault.get();
        if (provider != null) {
            return load(provider, who, node, byDefault);
        }
        return CompletableFuture.completedFuture(answer(byDefault));
    }

    private CompletableFuture<Answer> load(NodeStore store, PlayerRef who, String node, boolean byDefault) {
        CompletableFuture<Answer> answer = new CompletableFuture<>();
        try {
            NodeStore.Load load = store.prepare(who.uuid(), node);
            scheduler.async(() -> run(load, who, node, byDefault, answer));
        } catch (RuntimeException refused) {
            // The store could not start the read, or the scheduler refuses work while the plugin stops.
            unread(who, node, answer);
        }
        return answer;
    }

    private void run(
            NodeStore.Load load, PlayerRef who, String node, boolean byDefault, CompletableFuture<Answer> answer) {
        try {
            var unused = load.run().whenComplete((set, failure) -> {
                if (failure != null || set == null) {
                    unread(who, node, answer);
                } else {
                    answer.complete(answer(set.orElse(byDefault)));
                }
            });
        } catch (RuntimeException failure) {
            unread(who, node, answer);
        }
    }

    private void unread(PlayerRef who, String node, CompletableFuture<Answer> answer) {
        log.warn("event=stored_permission_unread player={} node={}", who.name(), node);
        answer.complete(Answer.NO_ANSWER);
    }

    /** The node's declared default against the stored operator status; an undeclared node defaults to operators. */
    private boolean byDefault(UUID player, String node) {
        Permission declared = server.getPluginManager().getPermission(node);
        PermissionDefault fallback = declared == null ? Permission.DEFAULT_PERMISSION : declared.getDefault();
        return fallback.getValue(isOperator(player));
    }

    /** Read from the operator list itself, which answers for a player who is not online. */
    private boolean isOperator(UUID player) {
        return server.getOperators().stream().anyMatch(op -> op.getUniqueId().equals(player));
    }

    private Optional<String> anotherBackend(UUID player) {
        return presence.find(player).map(OnlinePlayer::server).filter(name -> !name.equals(presence.localServer()));
    }

    /**
     * The permission plugin registered with Vault, or null when there is none: Vault's own fallback registers under
     * Vault itself, and only a real permission plugin under another name. Looked up by name so Vault stays optional.
     */
    private static @Nullable NodeStore vaultProvider(Server server) {
        Optional<Class<?>> service = server.getServicesManager().getKnownServices().stream()
                .filter(known -> known.getName().equals(VAULT_PERMISSION))
                .findFirst();
        if (service.isEmpty()) {
            return null;
        }
        for (RegisteredServiceProvider<?> registration :
                server.getServicesManager().getRegistrations(service.get())) {
            if (!registration.getPlugin().getName().equals(VAULT)) {
                // Past the lookup Vault is known to be loaded, so its types resolve.
                return new VaultNodeStore(server, registration);
            }
        }
        return null;
    }

    private static Answer answer(boolean held) {
        return held ? Answer.HELD : Answer.NOT_HELD;
    }
}
