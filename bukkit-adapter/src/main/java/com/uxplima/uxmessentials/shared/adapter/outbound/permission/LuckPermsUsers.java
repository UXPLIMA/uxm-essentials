package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import org.jspecify.annotations.NullMarked;

/** The LuckPerms storage as {@link LuckPermsNodeStore} reads it. Loaded only once LuckPerms is known to be there. */
@NullMarked
final class LuckPermsUsers implements LuckPermsNodeStore.Users {

    private final LuckPerms luckPerms;

    LuckPermsUsers() {
        this.luckPerms = LuckPermsProvider.get();
    }

    @Override
    public CompletableFuture<Optional<String>> knownName(UUID player) {
        return luckPerms
                .getUserManager()
                .lookupUsername(Objects.requireNonNull(player, "player"))
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Boolean> grants(UUID player, String node) {
        Objects.requireNonNull(node, "node");
        return luckPerms
                .getUserManager()
                .loadUser(Objects.requireNonNull(player, "player"))
                .thenApply(user -> grants(user, node));
    }

    @Override
    public CompletableFuture<Boolean> grantsNewcomers(String node) {
        Objects.requireNonNull(node, "node");
        // The default group is always loaded, so this reads memory; it still answers as a future like the user reads.
        Group joinedInto = luckPerms.getGroupManager().getGroup("default");
        return CompletableFuture.completedFuture(joinedInto != null && grants(joinedInto, node));
    }

    /**
     * Whether {@code holder} holds {@code node} in any context, directly, through a group, or through a wildcard.
     */
    private static boolean grants(PermissionHolder holder, String node) {
        QueryOptions everywhere = QueryOptions.nonContextual();
        if (holder.getCachedData().getPermissionData(everywhere).checkPermission(node) == Tristate.TRUE) {
            return true;
        }
        // A denial in one context can hide a grant in another from the merged answer; any grant counts.
        return holder.resolveInheritedNodes(everywhere).stream()
                .anyMatch(held -> held.getKey().equals(node) && held.getValue());
    }
}
