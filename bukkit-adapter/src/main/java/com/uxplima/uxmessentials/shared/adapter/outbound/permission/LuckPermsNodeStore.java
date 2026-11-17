package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NodeStore} backed by LuckPerms. {@code loadUser} reads the user from the LuckPerms storage when they are
 * not loaded here, which on a network sharing that storage is the same data every backend applies. It is constructed
 * only past {@link LuckPermsAccess#isPresent}, so a server without LuckPerms never loads the LuckPerms types, which
 * live in {@link LuckPermsUsers} alone.
 *
 * <p>The node is read without contexts: a grant held only with {@code server=survival} or a {@code world=} context
 * counts, wherever the read is made. A sanction reaches the player on every server and in every world, so a grant
 * that exempts them anywhere exempts them from it.
 *
 * <p>LuckPerms makes up an empty user for an account its storage has never seen, so the made-up user's answer is not
 * taken as it stands. An account its storage has no name for is either one an admin gave nodes by uuid before it ever
 * joined, read for those nodes like any stored user, or one with no nodes of its own, which joins into the
 * {@code default} group and is read for what that group grants. A player who played only where another LuckPerms storage holds
 * their grants reads the same way here, which is why the backends' own record of the exemption is read beside this
 * store.
 */
@NullMarked
final class LuckPermsNodeStore implements NodeStore {

    private final Users users;

    LuckPermsNodeStore() {
        this(new LuckPermsUsers());
    }

    LuckPermsNodeStore(Users users) {
        this.users = Objects.requireNonNull(users, "users");
    }

    @Override
    public Load prepare(UUID player, String node) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(node, "node");
        return () -> users.knownName(player)
                .thenCompose(name -> name.isPresent()
                        ? users.grants(player, node)
                        : users.holdsOwnNodes(player)
                                .thenCompose(own -> own ? users.grants(player, node) : users.grantsNewcomers(node)))
                .thenApply(held -> held ? Answer.HELD : Answer.NOT_HELD);
    }

    /** The reads this store makes of the LuckPerms storage, all off the tick. */
    interface Users {

        /** The name the storage holds for {@code player}, empty when it has never seen them. */
        CompletableFuture<Optional<String>> knownName(UUID player);

        /**
         * Whether the storage holds nodes for {@code player} beyond the {@code default} group membership LuckPerms
         * gives everyone: an admin granted them something by uuid, whether or not they ever joined.
         */
        CompletableFuture<Boolean> holdsOwnNodes(UUID player);

        /** Whether the stored {@code player} holds {@code node} in any context. */
        CompletableFuture<Boolean> grants(UUID player, String node);

        /** Whether the {@code default} group every account joins into grants {@code node} in any context. */
        CompletableFuture<Boolean> grantsNewcomers(String node);
    }
}
