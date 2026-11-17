package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
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
 * Reads a node for a player who is not online here. It fails closed: a grant found anywhere counts, and the node is
 * answered as not held only when every source that could grant it was read and none did.
 *
 * <p>A player online on another backend is asked about over the bus first, and that backend reads the node live: that
 * answer is the truth. Without a bus, or without an answer, the LuckPerms storage is read when LuckPerms is installed;
 * otherwise there is {@link Answer#NO_SOURCE}.
 *
 * <p>An offline player is read from every source this server has, and any one granting the node is enough:
 *
 * <ul>
 *   <li>LuckPerms, loaded from its storage on the async lane, in any context (see {@link LuckPermsNodeStore});
 *   <li>the permission plugin registered with Vault, when LuckPerms is not that plugin (see {@link VaultNodeStore});
 *   <li>the node's declared default against this backend's own operator list, including the defaults of the
 *       permissions that grant it as a child. On a network each backend has its own operator list, and this one is
 *       read.
 * </ul>
 *
 * <p>Everything taken from the server (the defaults, the operator list, the provider, an offline player's name) is
 * read on the calling server thread before anything leaves it.
 */
@NullMarked
public final class ServerStoredPermissions implements StoredPermissions {

    private static final String VAULT_PERMISSION = "net.milkbowl.vault.permission.Permission";
    private static final String LUCKPERMS = "LuckPerms";

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
     * The reader for this server: LuckPerms when it is enabled, the Vault permission provider, and the server's own
     * defaults and operators; for a player on another backend, that backend through {@code bus} (empty off the network).
     */
    public static ServerStoredPermissions of(
            Server server,
            Scheduler scheduler,
            NetworkPresence presence,
            Logger log,
            Optional<BusPermissionReads> bus) {
        // Constructed by a direct new past the guard, so a server without LuckPerms never loads its classes.
        NodeStore luckPerms = LuckPermsAccess.isPresent(server) ? new LuckPermsNodeStore() : null;
        boolean luckPermsRead = luckPerms != null;
        return new ServerStoredPermissions(
                server, scheduler, presence, log, luckPerms, () -> vaultProvider(server, luckPermsRead), bus);
    }

    @Override
    public CompletableFuture<Answer> read(PlayerRef who, String node) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(node, "node");
        boolean byDefault = byDefault(who.uuid(), node);
        Optional<String> elsewhere = anotherBackend(who.uuid());
        if (elsewhere.isPresent()) {
            CompletableFuture<Answer> live = bus.map(reads -> reads.ask(who, node, elsewhere.get()))
                    .orElseGet(() -> CompletableFuture.completedFuture(Answer.NO_SOURCE));
            NodeStore stored = luckPerms;
            if (stored == null) {
                return live;
            }
            // The LuckPerms store reads nothing from the server while it prepares, so it may start off the tick.
            return live.thenCompose(answer -> answer == Answer.HELD || answer == Answer.NOT_HELD
                    ? CompletableFuture.completedFuture(answer)
                    : combined(prepared(List.of(stored), who, node), byDefault));
        }
        List<NodeStore> stores = new ArrayList<>(2);
        if (luckPerms != null) {
            stores.add(luckPerms);
        }
        NodeStore provider = vault.get();
        if (provider != null) {
            stores.add(provider);
        }
        return combined(prepared(stores, who, node), byDefault);
    }

    /** Start each store's read here, on the calling thread; a store that cannot start counts as no answer. */
    private List<Pending> prepared(List<NodeStore> stores, PlayerRef who, String node) {
        List<Pending> loads = new ArrayList<>(stores.size());
        for (NodeStore store : stores) {
            try {
                loads.add(new Pending(store.prepare(who.uuid(), node), who, node));
            } catch (RuntimeException failure) {
                loads.add(new Pending(() -> CompletableFuture.failedFuture(failure), who, node));
            }
        }
        return loads;
    }

    /** The answer of every load and the default together: any grant is held, any miss keeps it from not held. */
    private CompletableFuture<Answer> combined(List<Pending> loads, boolean byDefault) {
        if (byDefault) {
            return CompletableFuture.completedFuture(Answer.HELD);
        }
        if (loads.isEmpty()) {
            return CompletableFuture.completedFuture(Answer.NOT_HELD);
        }
        List<CompletableFuture<Answer>> answers = new ArrayList<>(loads.size());
        for (Pending load : loads) {
            CompletableFuture<Answer> answer = new CompletableFuture<>();
            answers.add(answer);
            try {
                scheduler.async(() -> load.run(answer));
            } catch (RuntimeException refused) {
                // The scheduler refuses work while the plugin stops.
                answer.complete(Answer.NO_ANSWER);
            }
        }
        return CompletableFuture.allOf(answers.toArray(CompletableFuture[]::new))
                .thenApply(done ->
                        combine(answers.stream().map(CompletableFuture::join).toList()));
    }

    private static Answer combine(List<Answer> answers) {
        if (answers.contains(Answer.HELD)) {
            return Answer.HELD;
        }
        if (answers.contains(Answer.NO_ANSWER)) {
            return Answer.NO_ANSWER;
        }
        return answers.contains(Answer.NO_SOURCE) ? Answer.NO_SOURCE : Answer.NOT_HELD;
    }

    /** One store's read, started on the server thread and run on the async lane. */
    private final class Pending {

        private final NodeStore.Load load;
        private final PlayerRef who;
        private final String node;

        Pending(NodeStore.Load load, PlayerRef who, String node) {
            this.load = load;
            this.who = who;
            this.node = node;
        }

        void run(CompletableFuture<Answer> answer) {
            try {
                var unused = load.run().whenComplete((read, failure) -> {
                    if (failure != null || read == null) {
                        unread(answer);
                    } else {
                        answer.complete(read);
                    }
                });
            } catch (RuntimeException failure) {
                unread(answer);
            }
        }

        private void unread(CompletableFuture<Answer> answer) {
            log.warn("event=stored_permission_unread player={} node={}", who.name(), node);
            answer.complete(Answer.NO_ANSWER);
        }
    }

    /**
     * Whether the server grants {@code node} to {@code player} by default against this backend's operator list: the
     * node's own default (an undeclared node defaults to operators) or the default of any permission that grants it
     * as a child, however deep.
     */
    private boolean byDefault(UUID player, String node) {
        boolean operator = isOperator(player);
        PluginManager plugins = server.getPluginManager();
        Set<Permission> declared = plugins.getPermissions();
        Deque<String> granting = new ArrayDeque<>(List.of(node));
        Set<String> seen = new HashSet<>();
        while (!granting.isEmpty()) {
            String current = granting.pop();
            if (!seen.add(current)) {
                continue;
            }
            Permission permission = plugins.getPermission(current);
            PermissionDefault fallback = permission == null ? Permission.DEFAULT_PERMISSION : permission.getDefault();
            if (fallback.getValue(operator)) {
                return true;
            }
            for (Permission parent : declared) {
                if (Boolean.TRUE.equals(parent.getChildren().get(current))) {
                    granting.push(parent.getName());
                }
            }
        }
        return false;
    }

    /** Read from the operator list itself, which answers for a player who is not online. */
    private boolean isOperator(UUID player) {
        return server.getOperators().stream().anyMatch(op -> op.getUniqueId().equals(player));
    }

    private Optional<String> anotherBackend(UUID player) {
        return presence.find(player).map(OnlinePlayer::server).filter(name -> !name.equals(presence.localServer()));
    }

    /**
     * The active permission provider registered with Vault, or null when there is none to read. The highest-priority
     * registration is the one Vault itself hands out; with LuckPerms read directly, its own Vault registration adds
     * nothing. Looked up by name so Vault stays optional.
     */
    private static @Nullable NodeStore vaultProvider(Server server, boolean luckPermsRead) {
        Optional<Class<?>> service = server.getServicesManager().getKnownServices().stream()
                .filter(known -> known.getName().equals(VAULT_PERMISSION))
                .findFirst();
        if (service.isEmpty()) {
            return null;
        }
        RegisteredServiceProvider<?> registration = server.getServicesManager().getRegistration(service.get());
        if (registration == null
                || (luckPermsRead && registration.getPlugin().getName().equals(LUCKPERMS))) {
            return null;
        }
        // Past the lookup Vault is known to be loaded, so its types resolve.
        return VaultNodeStore.of(server, registration);
    }
}
