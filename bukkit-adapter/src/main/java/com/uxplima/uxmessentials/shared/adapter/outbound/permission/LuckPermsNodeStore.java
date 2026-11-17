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
 * <p>LuckPerms makes up an empty user for an account its storage has never seen, so such a user would read as
 * granting nothing. A player its storage has no name for (it is not shared with the server they played on, or they
 * never joined one that uses it) is therefore {@link Answer#NO_SOURCE}: this store cannot tell.
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
                .thenCompose(name -> name.isEmpty()
                        ? CompletableFuture.completedFuture(Answer.NO_SOURCE)
                        : users.grants(player, node).thenApply(held -> held ? Answer.HELD : Answer.NOT_HELD));
    }

    /** The two reads this store makes of the LuckPerms storage, both off the tick. */
    interface Users {

        /** The name the storage holds for {@code player}, empty when it has never seen them. */
        CompletableFuture<Optional<String>> knownName(UUID player);

        /** Whether the stored {@code player} holds {@code node} in any context. */
        CompletableFuture<Boolean> grants(UUID player, String node);
    }
}
