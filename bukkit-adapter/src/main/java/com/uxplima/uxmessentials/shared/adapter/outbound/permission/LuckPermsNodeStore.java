package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.util.Tristate;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NodeStore} backed by LuckPerms. {@code loadUser} reads the user from the LuckPerms storage when they are
 * not loaded here, which on a network sharing that storage is the same data every backend applies. It is constructed
 * only past {@link LuckPermsAccess#isPresent}, so a server without LuckPerms never loads this class.
 */
@NullMarked
final class LuckPermsNodeStore implements NodeStore {

    private final LuckPerms luckPerms;

    LuckPermsNodeStore() {
        this.luckPerms = LuckPermsProvider.get();
    }

    @Override
    public CompletableFuture<Optional<Boolean>> load(UUID player, String node) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(node, "node");
        return luckPerms.getUserManager().loadUser(player).thenApply(user -> {
            Tristate set = user.getCachedData().getPermissionData().checkPermission(node);
            return set == Tristate.UNDEFINED ? Optional.empty() : Optional.of(set.asBoolean());
        });
    }
}
