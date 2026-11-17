package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reads a node for a player who is not online here, the way this server would apply it if they joined.
 *
 * <p>With LuckPerms, the user is loaded from its storage on the async lane, never on the tick, and a node it sets
 * wins. A node it leaves unset falls back to the node's declared default against the player's stored operator status,
 * as it would for a player online here.
 *
 * <p>Without LuckPerms the server's own grants are the declared defaults and the operator list, and those answer for a
 * player who is offline. They do not answer when another permission plugin is registered (its stored grants cannot be
 * read here), nor for a player online on another backend, whose operator list and grants are that backend's: the
 * answer is then {@link Answer#NO_SOURCE}, never a guess.
 *
 * <p>The default and the operator status are read on the calling server thread before anything leaves it.
 */
@NullMarked
public final class ServerStoredPermissions implements StoredPermissions {

    private static final String VAULT_PERMISSION = "net.milkbowl.vault.permission.Permission";
    private static final String VAULT = "Vault";

    private final Server server;
    private final Scheduler scheduler;
    private final NetworkPresence presence;
    private final Logger log;

    @Nullable private final NodeStore store;

    private final BooleanSupplier otherPermissionPlugin;

    ServerStoredPermissions(
            Server server,
            Scheduler scheduler,
            NetworkPresence presence,
            Logger log,
            @Nullable NodeStore store,
            BooleanSupplier otherPermissionPlugin) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.log = Objects.requireNonNull(log, "log");
        this.store = store;
        this.otherPermissionPlugin = Objects.requireNonNull(otherPermissionPlugin, "otherPermissionPlugin");
    }

    /** The reader for this server: the LuckPerms storage when LuckPerms is enabled, the server's own grants otherwise. */
    public static ServerStoredPermissions of(Server server, Scheduler scheduler, NetworkPresence presence, Logger log) {
        // Constructed by a direct new past the guard, so a server without LuckPerms never loads its classes.
        NodeStore luckPerms = LuckPermsAccess.isPresent(server) ? new LuckPermsNodeStore() : null;
        return new ServerStoredPermissions(
                server, scheduler, presence, log, luckPerms, () -> otherPermissionPlugin(server));
    }

    @Override
    public CompletableFuture<Answer> read(PlayerRef who, String node) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(node, "node");
        boolean byDefault = byDefault(who.uuid(), node);
        NodeStore loaded = store;
        if (loaded == null) {
            return CompletableFuture.completedFuture(
                    onAnotherBackend(who.uuid()) || otherPermissionPlugin.getAsBoolean()
                            ? Answer.NO_SOURCE
                            : answer(byDefault));
        }
        CompletableFuture<Answer> answer = new CompletableFuture<>();
        try {
            scheduler.async(() -> load(loaded, who, node, byDefault, answer));
        } catch (RuntimeException refused) {
            // The scheduler refuses work while the plugin stops.
            answer.complete(Answer.NO_ANSWER);
        }
        return answer;
    }

    private void load(
            NodeStore loaded, PlayerRef who, String node, boolean byDefault, CompletableFuture<Answer> answer) {
        try {
            var unused = loaded.load(who.uuid(), node).whenComplete((set, failure) -> {
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

    private boolean onAnotherBackend(UUID player) {
        return presence.find(player)
                .filter(online -> !online.server().equals(presence.localServer()))
                .isPresent();
    }

    /**
     * Whether a permission plugin is registered with Vault: Vault's own fallback registers under Vault itself, and
     * only a real permission plugin under another name. Read by name so Vault stays optional.
     */
    private static boolean otherPermissionPlugin(Server server) {
        Optional<Class<?>> service = server.getServicesManager().getKnownServices().stream()
                .filter(known -> known.getName().equals(VAULT_PERMISSION))
                .findFirst();
        if (service.isEmpty()) {
            return false;
        }
        for (RegisteredServiceProvider<?> provider : server.getServicesManager().getRegistrations(service.get())) {
            if (!provider.getPlugin().getName().equals(VAULT)) {
                return true;
            }
        }
        return false;
    }

    private static Answer answer(boolean held) {
        return held ? Answer.HELD : Answer.NOT_HELD;
    }
}
